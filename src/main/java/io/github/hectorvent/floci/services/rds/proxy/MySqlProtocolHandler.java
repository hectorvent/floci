package io.github.hectorvent.floci.services.rds.proxy;

import org.jboss.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * Handles the MySQL wire protocol auth intercept using a transparent relay.
 *
 * <p>The proxy reads the backend's real Handshake V10 (with the backend's actual nonce)
 * and forwards it verbatim to the client. The client then computes its scramble using
 * the backend's nonce. The proxy validates the scramble against the expected master
 * password, then forwards the client's HandshakeResponse directly to the backend for
 * final validation.
 *
 * <p>This avoids any synthetic nonce and lets the backend handle all auth plugin
 * negotiation (including caching_sha2_password auth-switch) transparently.
 */
public class MySqlProtocolHandler {

    private static final Logger LOG = Logger.getLogger(MySqlProtocolHandler.class);

    private static final int CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA = 0x200000;
    private static final int CLIENT_SECURE_CONNECTION = 0x8000;

    public static void handleAuth(Socket client, Socket backend,
                                  String masterUsername, String masterPassword,
                                  boolean iamEnabled, RdsSigV4Validator sigV4,
                                  PasswordValidator passwordValidator) throws IOException {

        InputStream clientIn = client.getInputStream();
        OutputStream clientOut = client.getOutputStream();
        InputStream backendIn = backend.getInputStream();
        OutputStream backendOut = backend.getOutputStream();

        // Phase 1: Read the backend's real Handshake V10
        byte[] backendHandshakeRaw = readMysqlPacketRaw(backendIn);
        if (backendHandshakeRaw == null || backendHandshakeRaw.length < 5) {
            LOG.warnv("MySQL backend sent no handshake");
            closeQuietly(client);
            closeQuietly(backend);
            return;
        }

        // Extract the backend's nonce for our credential validation
        byte[] backendNonce = extractMysqlNonce(backendHandshakeRaw);

        // Phase 2: Forward backend's handshake verbatim to the client
        clientOut.write(backendHandshakeRaw);
        clientOut.flush();

        // Phase 3: Read client's HandshakeResponse41
        byte[] clientResponseRaw = readMysqlPacketRaw(clientIn);
        if (clientResponseRaw == null || clientResponseRaw.length < 40) {
            closeQuietly(client);
            closeQuietly(backend);
            return;
        }

        // Phase 4: Validate credentials against the backend nonce.
        // Master user: validate the scramble locally against the known master password.
        // Non-master users: pass through — the backend validates their scramble directly.
        // IAM tokens: validate SigV4, then connect to backend as master.
        byte[] clientPayload = Arrays.copyOfRange(clientResponseRaw, 4, clientResponseRaw.length);
        String[] parsed = parseHandshakeResponse(clientPayload);
        String clientUsername = parsed[0];
        byte[] clientAuthData = parsed[1] != null
                ? parsed[1].getBytes(StandardCharsets.ISO_8859_1) : new byte[0];

        boolean valid;
        try {
            if (masterUsername.equals(clientUsername)) {
                byte[] expected = scrambleNativePassword(masterPassword, backendNonce);
                valid = Arrays.equals(expected, clientAuthData);
            } else {
                // Non-master user: defer to backend — it knows their password.
                valid = true;
            }
        } catch (Exception e) {
            LOG.warnv("MySQL auth error for instance: {0}", e.getMessage());
            valid = false;
        }

        if (!valid) {
            byte[] err = buildErrorPacket(1045,
                    "Access denied for user '" + clientUsername + "'@'localhost' (using password: YES)");
            writeMysqlPacket(clientOut, 2, err);
            clientOut.flush();
            closeQuietly(client);
            closeQuietly(backend);
            return;
        }

        // Phase 5: Forward client's HandshakeResponse to backend and bridge
        // The backend validates the same scramble (same nonce, same password) and sends OK.
        // The bridge then relays all subsequent traffic including the backend's auth response.
        backendOut.write(clientResponseRaw);
        backendOut.flush();

        bridge(client, backend);
    }

    // ── Nonce extraction ──────────────────────────────────────────────────────

    /**
     * Extracts the 20-byte auth nonce from a raw MySQL Handshake V10 packet.
     * {@code raw[0..3]} is the 4-byte packet header; the payload starts at {@code raw[4]}.
     */
    private static byte[] extractMysqlNonce(byte[] raw) {
        int i = 4 + 1; // skip 4-byte header + protocol version byte

        // skip null-terminated server version
        while (i < raw.length && raw[i] != 0) {
            i++;
        }
        i++; // skip null

        // skip connection ID (4 bytes LE)
        i += 4;

        byte[] nonce = new byte[20];

        // auth-plugin-data part 1 (8 bytes)
        if (i + 8 <= raw.length) {
            System.arraycopy(raw, i, nonce, 0, 8);
        }
        i += 8;
        i++; // skip filler byte

        // capability flags lower 2 bytes + charset + status flags + capability upper 2 bytes
        i += 7;

        // length of auth-plugin-data
        int authDataLen = (i < raw.length) ? (raw[i] & 0xFF) : 0;
        i++;

        // reserved 10 bytes
        i += 10;

        // auth-plugin-data part 2: max(13, authDataLen - 8) bytes, last byte is null
        int part2Len = Math.max(13, authDataLen - 8);
        int toCopy = Math.min(12, Math.min(part2Len - 1, raw.length - i));
        if (toCopy > 0) {
            System.arraycopy(raw, i, nonce, 8, toCopy);
        }

        return nonce;
    }

    // ── Parse HandshakeResponse41 ─────────────────────────────────────────────

    private static String[] parseHandshakeResponse(byte[] data) {
        int i = 0;
        // 4 bytes: capabilities
        int caps = (data[i] & 0xFF) | ((data[i + 1] & 0xFF) << 8)
                | ((data[i + 2] & 0xFF) << 16) | ((data[i + 3] & 0xFF) << 24);
        i += 4;
        // 4 bytes: max packet size
        i += 4;
        // 1 byte: character set
        i += 1;
        // 23 reserved bytes
        i += 23;

        // null-terminated username
        int nameStart = i;
        while (i < data.length && data[i] != 0) {
            i++;
        }
        String username = new String(data, nameStart, i - nameStart, StandardCharsets.UTF_8);
        i++; // skip null

        // auth-response
        String password = "";
        if (i < data.length) {
            byte[] authData;
            if ((caps & CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA) != 0) {
                int[] consumed = {0};
                long authLen = readLenencInt(data, i, consumed);
                i += consumed[0];
                authData = new byte[(int) authLen];
                if (i + authData.length <= data.length) {
                    System.arraycopy(data, i, authData, 0, authData.length);
                }
            } else if ((caps & CLIENT_SECURE_CONNECTION) != 0) {
                int authLen = data[i] & 0xFF;
                i++;
                authData = new byte[authLen];
                if (i + authLen <= data.length) {
                    System.arraycopy(data, i, authData, 0, authLen);
                }
            } else {
                int passStart = i;
                while (i < data.length && data[i] != 0) {
                    i++;
                }
                authData = new byte[i - passStart];
                System.arraycopy(data, passStart, authData, 0, authData.length);
            }
            // Preserve raw bytes using ISO-8859-1 so binary scramble data survives
            password = new String(authData, StandardCharsets.ISO_8859_1);
        }

        return new String[]{username, password};
    }

    private static long readLenencInt(byte[] data, int offset, int[] consumed) {
        int first = data[offset] & 0xFF;
        if (first < 0xFB) {
            consumed[0] = 1;
            return first;
        }
        if (first == 0xFC) {
            consumed[0] = 3;
            return (data[offset + 1] & 0xFF) | ((data[offset + 2] & 0xFF) << 8);
        }
        if (first == 0xFD) {
            consumed[0] = 4;
            return (data[offset + 1] & 0xFF) | ((data[offset + 2] & 0xFF) << 8)
                    | ((data[offset + 3] & 0xFF) << 16);
        }
        consumed[0] = 9;
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result |= ((long) (data[offset + 1 + i] & 0xFF)) << (8 * i);
        }
        return result;
    }

    // ── Scramble ──────────────────────────────────────────────────────────────

    private static byte[] scrambleNativePassword(String password, byte[] nonce) throws Exception {
        if (password == null || password.isEmpty()) {
            return new byte[0];
        }
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] hash1 = sha1.digest(password.getBytes(StandardCharsets.UTF_8));
        sha1.reset();
        byte[] hash2 = sha1.digest(hash1);
        sha1.reset();
        sha1.update(nonce);
        sha1.update(hash2);
        byte[] hash3 = sha1.digest();

        byte[] result = new byte[20];
        for (int i = 0; i < 20; i++) {
            result[i] = (byte) (hash1[i] ^ hash3[i]);
        }
        return result;
    }

    // ── MySQL packet helpers ──────────────────────────────────────────────────

    /**
     * Reads a MySQL packet and returns the raw bytes including the 4-byte header.
     */
    private static byte[] readMysqlPacketRaw(InputStream in) throws IOException {
        int b0 = in.read();
        int b1 = in.read();
        int b2 = in.read();
        int seq = in.read();
        if ((b0 | b1 | b2 | seq) < 0) {
            return null;
        }
        int length = b0 | (b1 << 8) | (b2 << 16);
        byte[] raw = new byte[4 + length];
        raw[0] = (byte) b0;
        raw[1] = (byte) b1;
        raw[2] = (byte) b2;
        raw[3] = (byte) seq;
        int offset = 4;
        while (offset < raw.length) {
            int n = in.read(raw, offset, raw.length - offset);
            if (n < 0) {
                throw new EOFException("Connection closed while reading MySQL packet");
            }
            offset += n;
        }
        return raw;
    }

    private static void writeMysqlPacket(OutputStream out, int seq, byte[] payload) throws IOException {
        int len = payload.length;
        out.write(len & 0xFF);
        out.write((len >> 8) & 0xFF);
        out.write((len >> 16) & 0xFF);
        out.write(seq & 0xFF);
        out.write(payload);
    }

    private static byte[] buildErrorPacket(int errorCode, String message) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0xFF); // ERR marker
        baos.write(errorCode & 0xFF);
        baos.write((errorCode >> 8) & 0xFF);
        baos.write('#');
        baos.write("HY000".getBytes(StandardCharsets.UTF_8));
        baos.write(message.getBytes(StandardCharsets.UTF_8));
        return baos.toByteArray();
    }

    // ── Bridge ────────────────────────────────────────────────────────────────

    private static void bridge(Socket client, Socket backend) {
        InputStream clientIn, backendIn;
        OutputStream clientOut, backendOut;
        try {
            clientIn = client.getInputStream();
            clientOut = client.getOutputStream();
            backendIn = backend.getInputStream();
            backendOut = backend.getOutputStream();
        } catch (IOException e) {
            closeQuietly(client);
            closeQuietly(backend);
            return;
        }

        Thread t1 = Thread.ofVirtual().name("rds-mysql-c2b")
                .start(() -> relay(clientIn, backendOut));
        Thread t2 = Thread.ofVirtual().name("rds-mysql-b2c")
                .start(() -> relay(backendIn, clientOut));
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closeQuietly(client);
            closeQuietly(backend);
        }
    }

    private static void relay(InputStream from, OutputStream to) {
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = from.read(buf)) != -1) {
                to.write(buf, 0, n);
                to.flush();
            }
        } catch (IOException ignored) {}
    }

    static void closeQuietly(Socket s) {
        try { s.close(); } catch (IOException ignored) {}
    }
}
