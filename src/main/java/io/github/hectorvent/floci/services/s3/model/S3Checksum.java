package io.github.hectorvent.floci.services.s3.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@RegisterForReflection
public class S3Checksum {

    private String checksumCRC32;
    private String checksumCRC32C;
    private String checksumCRC64NVME;
    private String checksumSHA1;
    private String checksumSHA256;
    private String checksumType;

    public String getChecksumCRC32() { return checksumCRC32; }
    public void setChecksumCRC32(String checksumCRC32) { this.checksumCRC32 = checksumCRC32; }

    public String getChecksumCRC32C() { return checksumCRC32C; }
    public void setChecksumCRC32C(String checksumCRC32C) { this.checksumCRC32C = checksumCRC32C; }

    public String getChecksumCRC64NVME() { return checksumCRC64NVME; }
    public void setChecksumCRC64NVME(String checksumCRC64NVME) { this.checksumCRC64NVME = checksumCRC64NVME; }

    public String getChecksumSHA1() { return checksumSHA1; }
    public void setChecksumSHA1(String checksumSHA1) { this.checksumSHA1 = checksumSHA1; }

    public String getChecksumSHA256() { return checksumSHA256; }
    public void setChecksumSHA256(String checksumSHA256) { this.checksumSHA256 = checksumSHA256; }

    public String getChecksumType() { return checksumType; }
    public void setChecksumType(String checksumType) { this.checksumType = checksumType; }

    public boolean hasAnyValue() {
        return checksumCRC32 != null || checksumCRC32C != null || checksumCRC64NVME != null
                || checksumSHA1 != null || checksumSHA256 != null;
    }

    public static String sha256Base64(byte[] data) {
        return digestBase64("SHA-256", data);
    }

    public static String sha1Base64(byte[] data) {
        return digestBase64("SHA-1", data);
    }

    private static String digestBase64(String algorithm, byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return Base64.getEncoder().encodeToString(digest.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing digest algorithm: " + algorithm, e);
        }
    }
}
