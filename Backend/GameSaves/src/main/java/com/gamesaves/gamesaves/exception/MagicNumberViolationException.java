package com.gamesaves.gamesaves.exception;

/**
 * Thrown when a file inside a ZIP archive has a magic number (file header)
 * that contradicts its claimed extension — indicating a disguised executable.
 */
public class MagicNumberViolationException extends RuntimeException {

    public MagicNumberViolationException(String message) {
        super(message);
    }
}
