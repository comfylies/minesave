package com.gamesaves.gamesaves.util;

import com.gamesaves.gamesaves.exception.FileProcessingException;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility for computing file hashes (MD5 for content-addressing, SHA-256 for ZIP integrity).
 */
@UtilityClass
public class FileHasher {

    private static final int BUFFER_SIZE = 8192;

    public static String md5(InputStream inputStream) {
        return digest("MD5", inputStream);
    }

    public static String sha256(InputStream inputStream) {
        return digest("SHA-256", inputStream);
    }

    private static String digest(String algorithm, InputStream inputStream) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            try (DigestInputStream dis = new DigestInputStream(inputStream, md)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (dis.read(buffer) != -1) {
                    // consume stream
                }
            }
            return bytesToHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new FileProcessingException(algorithm + " algorithm not available", e);
        } catch (IOException e) {
            throw new FileProcessingException("Failed to compute hash", e);
        }
    }

    public static String md5OfBytes(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new FileProcessingException("MD5 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
