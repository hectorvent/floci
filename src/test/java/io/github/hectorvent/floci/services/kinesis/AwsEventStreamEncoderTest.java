package io.github.hectorvent.floci.services.kinesis;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class AwsEventStreamEncoderTest {

    private static long crc32(byte[] data, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, offset, length);
        return crc.getValue();
    }

    @Test
    void encodeEventProducesValidFrame() throws Exception {
        byte[] payload = "{\"Records\":[]}".getBytes(StandardCharsets.UTF_8);
        byte[] frame = AwsEventStreamEncoder.encodeEvent("SubscribeToShardEvent", "application/json", payload);

        ByteBuffer buf = ByteBuffer.wrap(frame);
        int totalLength = buf.getInt(0);
        assertEquals(frame.length, totalLength, "total_byte_length must match actual frame length");

        // Prelude CRC: CRC32 of first 8 bytes
        long expectedPreludeCrc = crc32(frame, 0, 8);
        long actualPreludeCrc = Integer.toUnsignedLong(buf.getInt(8));
        assertEquals(expectedPreludeCrc, actualPreludeCrc, "Prelude CRC mismatch");

        // Message CRC: CRC32 of all bytes except last 4
        long expectedMsgCrc = crc32(frame, 0, frame.length - 4);
        long actualMsgCrc = Integer.toUnsignedLong(buf.getInt(frame.length - 4));
        assertEquals(expectedMsgCrc, actualMsgCrc, "Message CRC mismatch");

        // Payload must be present somewhere in the frame
        String frameStr = new String(frame, StandardCharsets.ISO_8859_1);
        assertTrue(frameStr.contains("{\"Records\":[]}"), "Payload bytes should appear in the frame");
    }

    @Test
    void encodeEventContainsExpectedHeaders() throws Exception {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);
        byte[] frame = AwsEventStreamEncoder.encodeEvent("SubscribeToShardEvent", "application/json", payload);
        String frameStr = new String(frame, StandardCharsets.ISO_8859_1);

        assertTrue(frameStr.contains(":message-type"), "Frame must contain ':message-type' header");
        assertTrue(frameStr.contains("event"), "Frame must contain 'event' header value");
        assertTrue(frameStr.contains(":event-type"), "Frame must contain ':event-type' header");
        assertTrue(frameStr.contains("SubscribeToShardEvent"), "Frame must contain 'SubscribeToShardEvent' header value");
        assertTrue(frameStr.contains(":content-type"), "Frame must contain ':content-type' header");
        assertTrue(frameStr.contains("application/json"), "Frame must contain 'application/json' header value");
    }
}