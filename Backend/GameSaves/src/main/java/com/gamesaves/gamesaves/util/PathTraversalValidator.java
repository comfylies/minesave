package com.gamesaves.gamesaves.util;

import com.gamesaves.gamesaves.exception.PathTraversalException;
import lombok.experimental.UtilityClass;

/**
 * Validates file paths to prevent directory traversal attacks (Zip Slip).
 */
@UtilityClass
public class PathTraversalValidator {

    public static void validate(String entryName) {
        if (entryName == null || entryName.isEmpty()) {
            throw new PathTraversalException("Entry name is empty");
        }

        // Reject absolute paths
        if (entryName.startsWith("/") || entryName.startsWith("\\")) {
            throw new PathTraversalException("Absolute path not allowed: " + entryName);
        }

        // Reject Windows drive letters
        if (entryName.matches("^[A-Za-z]:[/\\\\].*")) {
            throw new PathTraversalException("Absolute path (drive letter) not allowed: " + entryName);
        }

        // Reject traversal sequences
        if (entryName.contains("..")) {
            throw new PathTraversalException("Path traversal detected: " + entryName);
        }

        // Reject hidden directory traversal on Unix
        if (entryName.contains("./") || entryName.contains(".\\")) {
            throw new PathTraversalException("Path traversal detected: " + entryName);
        }
    }

    /**
     * Computes the parent path from a virtual path.
     * e.g., "region/r.0.0.mca" -> "region/"
     *        "level.dat" -> ""
     *        "DIM-1/data/chunks.dat" -> "DIM-1/data/"
     */
    public static String computeParentPath(String virtualPath) {
        if (virtualPath == null || virtualPath.isEmpty()) {
            return "";
        }
        int lastSlash = virtualPath.lastIndexOf('/');
        if (lastSlash < 0) {
            return "";
        }
        return virtualPath.substring(0, lastSlash + 1);
    }
}
