package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.RateLimitException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱验证码服务。
 *
 * <p>生成6位数字验证码并通过邮件发送，内存存储，定时清理过期数据。
 * 安全防护：每日上限5次 + 发送后2分钟冷却 + 发送频率限制 + 验证码有效期。
 *
 * <p>引用的开源项目：
 * <ul>
 *   <li><b>Spring Boot Mail</b> (Apache-2.0) — {@link org.springframework.mail.javamail.JavaMailSender}，
 *       底层封装 JavaMail（Eclipse Angus Mail / Jakarta Mail）</li>
 * </ul>
 */
@Service
public class EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long USABLE_DELAY_MS = 120_000; // 发送后2分钟才能使用
    private static final int MAX_DAILY_SENDS = 5;       // 每日每邮箱上限5次

    private final JavaMailSender mailSender;
    private final String mailFrom;

    // 存储结构：email → CodeEntry(code, expireTime, createdTime)
    private final ConcurrentHashMap<String, CodeEntry> codeCache = new ConcurrentHashMap<>();

    // 每日发送次数追踪：email → DailyCount
    private final ConcurrentHashMap<String, DailyCount> dailyCounts = new ConcurrentHashMap<>();

    @Value("${app.login.email-code-cooldown:60}")
    private int cooldownSeconds;

    @Value("${app.login.email-code-ttl:300}")
    private int ttlSeconds;

    public EmailCodeService(JavaMailSender mailSender,
                            @Value("${spring.mail.username}") String mailFrom) {
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }

    /**
     * 发送邮箱验证码
     * @param email 目标邮箱
     * @throws RateLimitException 发送过于频繁或超过每日上限
     */
    public void sendCode(String email) {
        // 检查每日发送上限
        checkDailyLimit(email);

        // 检查发送频率
        CodeEntry existing = codeCache.get(email);
        long now = System.currentTimeMillis();
        if (existing != null) {
            long elapsed = (now - existing.createdTime) / 1000;
            if (elapsed < cooldownSeconds) {
                long waitSeconds = cooldownSeconds - elapsed;
                throw new RateLimitException(
                        "Please wait " + waitSeconds + " seconds before requesting a new code");
            }
        }

        // 生成6位数字验证码
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));

        // 存储验证码（过期时间 = 当前时间 + TTL，usableAfter = 当前时间 + 2min冷却）
        codeCache.put(email, new CodeEntry(code, now + ttlSeconds * 1000L, now));

        // 递增每日计数
        incrementDailyCount(email);

        // 发送邮件
        try {
            sendEmail(email, code);
            log.info("Email verification code sent to {} (daily count: {}/{})",
                    maskEmail(email), getDailyCount(email), MAX_DAILY_SENDS);
        } catch (MessagingException e) {
            codeCache.remove(email); // 发送失败则清除
            log.error("Failed to send verification email to {}", maskEmail(email), e);
            throw new BadRequestException("Failed to send verification email, please try again later");
        }
    }

    /**
     * 验证邮箱验证码
     * @param email 邮箱
     * @param code 用户输入的验证码
     * @return true=验证通过, false=验证码错误/过期/冷却未到
     */
    public boolean verifyCode(String email, String code) {
        CodeEntry entry = codeCache.get(email);
        if (entry == null) {
            return false; // 未发送过验证码
        }

        long now = System.currentTimeMillis();

        // 检查是否已过冷却期（发送后2分钟内不能使用）
        if (now < entry.createdTime + USABLE_DELAY_MS) {
            log.info("Email code verification blocked: 2min cooldown not elapsed for {}", maskEmail(email));
            return false;
        }

        // 检查过期
        if (now > entry.expireTime) {
            codeCache.remove(email);
            return false;
        }

        // 验证通过后删除（一次性使用）
        if (entry.code.equals(code)) {
            codeCache.remove(email);
            return true;
        }
        return false;
    }

    /**
     * 获取今日已发送次数
     */
    public int getDailyCount(String email) {
        DailyCount dc = dailyCounts.get(email);
        if (dc == null) return 0;
        if (!dc.date.equals(LocalDate.now())) return 0;
        return dc.count;
    }

    /**
     * 获取每日最大发送次数
     */
    public int getMaxDailySends() {
        return MAX_DAILY_SENDS;
    }

    private void checkDailyLimit(String email) {
        DailyCount dc = dailyCounts.computeIfAbsent(email, k -> new DailyCount(0, LocalDate.now()));
        // 跨天重置
        if (!dc.date.equals(LocalDate.now())) {
            dc.count = 0;
            dc.date = LocalDate.now();
        }
        if (dc.count >= MAX_DAILY_SENDS) {
            throw new RateLimitException(
                    "Daily email code limit reached (" + MAX_DAILY_SENDS + " per day), please try again tomorrow");
        }
    }

    private void incrementDailyCount(String email) {
        DailyCount dc = dailyCounts.computeIfAbsent(email, k -> new DailyCount(0, LocalDate.now()));
        if (!dc.date.equals(LocalDate.now())) {
            dc.count = 1;
            dc.date = LocalDate.now();
        } else {
            dc.count++;
        }
    }

    /**
     * 定时清理过期验证码和每日计数（每分钟执行）
     */
    @Scheduled(fixedRate = 60000)
    public void cleanExpiredCodes() {
        long now = System.currentTimeMillis();
        codeCache.entrySet().removeIf(entry -> now > entry.getValue().expireTime);
        // 清理跨天的每日计数
        LocalDate today = LocalDate.now();
        dailyCounts.entrySet().removeIf(entry -> !entry.getValue().date.equals(today));
    }

    private void sendEmail(String to, String code) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(mailFrom);
        helper.setTo(to);
        helper.setSubject("MineSave - Email Verification Code");

        String htmlContent = """
                <div style="font-family: Arial, sans-serif; max-width: 500px; margin: 0 auto;
                            padding: 24px; background: #f9fafb; border-radius: 8px;">
                    <h2 style="color: #1a1a2e; text-align: center;">💾 MineSave</h2>
                    <p style="color: #555; font-size: 14px;">Your verification code is:</p>
                    <div style="text-align: center; padding: 16px; background: #ffffff;
                                border: 1px solid #e5e7eb; border-radius: 6px; margin: 16px 0;">
                        <span style="font-size: 32px; font-weight: bold; letter-spacing: 8px;
                                     color: #1a1a2e; font-family: 'Courier New', monospace;">%s</span>
                    </div>
                    <p style="color: #888; font-size: 12px;">
                        This code will expire in %d minutes.<br>
                        If you did not request this code, please ignore this email.
                    </p>
                </div>
                """.formatted(code, ttlSeconds / 60);

        helper.setText(htmlContent, true);
        mailSender.send(message);
    }

    /**
     * 邮箱脱敏：只显示前2个字符和域名
     */
    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) return name + "***@" + domain;
        return name.substring(0, 2) + "***@" + domain;
    }

    /**
     * 验证码存储条目
     */
    private record CodeEntry(String code, long expireTime, long createdTime) {
    }

    /**
     * 每日发送计数
     */
    private static class DailyCount {
        int count;
        LocalDate date;

        DailyCount(int count, LocalDate date) {
            this.count = count;
            this.date = date;
        }
    }
}
