package com.gamesaves.gamesaves.exception;

/**
 * 验证码验证失败异常
 */
public class CaptchaValidationException extends BadRequestException {

    public CaptchaValidationException(String message) {
        super(message);
    }
}
