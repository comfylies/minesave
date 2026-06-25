package com.gamesaves.gamesaves.exception;

/**
 * Thrown when a storage operation (upload, download, delete) fails.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
