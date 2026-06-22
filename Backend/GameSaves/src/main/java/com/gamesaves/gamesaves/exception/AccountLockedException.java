package com.gamesaves.gamesaves.exception;

/**
 * 账号锁定异常 — 连续登录失败超阈值触发
 */
public class AccountLockedException extends RuntimeException {

    private final long remainingMinutes;

    public AccountLockedException(long remainingMinutes) {
        super("Account locked. Please try again in " + remainingMinutes + " minutes");
        this.remainingMinutes = remainingMinutes;
    }

    public long getRemainingMinutes() {
        return remainingMinutes;
    }
}
