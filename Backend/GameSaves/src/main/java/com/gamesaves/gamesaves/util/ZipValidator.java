package com.gamesaves.gamesaves.util;

import lombok.experimental.UtilityClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Validates ZIP file magic numbers to ensure uploaded files are genuine ZIP archives.
 * Recognizes: PK\x03\x04 (standard), PK\x05\x06 (empty), PK\x07\x08 (spanned).
 */
@UtilityClass
public class ZipValidator {

    private static final Logger log = LoggerFactory.getLogger(ZipValidator.class);

    private static final byte[] ZIP_MAGIC = {0x50, 0x4B, 0x03, 0x04};       // PK\x03\x04
    private static final byte[] ZIP_EMPTY_MAGIC = {0x50, 0x4B, 0x05, 0x06}; // PK\x05\x06
    private static final byte[] ZIP_SPANNED_MAGIC = {0x50, 0x4B, 0x07, 0x08}; // PK\x07\x08

    public static boolean isValidZip(InputStream inputStream) {
        try {
            byte[] header = new byte[4];
            int read = inputStream.read(header);
            if (read < 4) {
                log.warn("File too small to be a ZIP (< 4 bytes)");
                return false;
            }
            boolean valid = Arrays.equals(header, ZIP_MAGIC)
                    || Arrays.equals(header, ZIP_EMPTY_MAGIC)
                    || Arrays.equals(header, ZIP_SPANNED_MAGIC);
            if (!valid) {
                log.warn("Invalid ZIP magic number: {}", bytesToHex(header));
            }
            return valid;
        } catch (IOException e) {
            log.error("Failed to read ZIP header", e);
            return false;
        }
    }

    public static boolean isValidZip(byte[] firstBytes) {
        if (firstBytes == null || firstBytes.length < 4) {
            return false;
        }
        byte[] header = Arrays.copyOf(firstBytes, 4);
        return Arrays.equals(header, ZIP_MAGIC)
                || Arrays.equals(header, ZIP_EMPTY_MAGIC)
                || Arrays.equals(header, ZIP_SPANNED_MAGIC);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
