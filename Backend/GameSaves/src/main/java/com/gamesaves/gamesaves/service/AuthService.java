package com.gamesaves.gamesaves.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import com.gamesaves.gamesaves.dto.request.*;
import com.gamesaves.gamesaves.dto.response.LoginResponse;
import com.gamesaves.gamesaves.dto.response.UserResponse;
import com.gamesaves.gamesaves.entity.LoginFail;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.exception.AccountLockedException;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.CaptchaValidationException;
import com.gamesaves.gamesaves.repository.LoginFailRepository;
import com.gamesaves.gamesaves.repository.UserRepository;
import com.gamesaves.gamesaves.util.CaptchaUtil;
import com.gamesaves.gamesaves.util.XssFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证服务 — 账号密码/邮箱验证码登录、注册、图形验证码、登录锁定。
 *
 * <p>引用的开源项目：
 * <ul>
 *   <li><b>Sa-Token</b> (Apache-2.0) — 轻量级权限认证框架，处理登录会话、token 签发与鉴权</li>
 *   <li><b>Spring Security Crypto</b> (Apache-2.0) — BCryptPasswordEncoder 密码哈希</li>
 *   <li><b>CaptchaUtil</b> — 自研，纯 JDK {@link java.awt} 实现，无第三方验证码库依赖</li>
 * </ul>
 */
@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // 图形验证码缓存：captchaKey → code
    private final ConcurrentHashMap<String, CaptchaCacheEntry> captchaCache = new ConcurrentHashMap<>();

    // 字段可用性检查限流：ip → lastCheckTimestamp
    private final ConcurrentHashMap<String, Long> fieldCheckRateLimiter = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final LoginFailRepository loginFailRepository;
    private final EmailCodeService emailCodeService;
    private final StorageService storageService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${app.login.max-fail-count:5}")
    private int maxFailCount;

    @Value("${app.login.lock-minutes:30}")
    private int lockMinutes;

    @Value("${app.login.captcha-required:true}")
    private boolean captchaRequired;

    public AuthService(UserRepository userRepository,
                       LoginFailRepository loginFailRepository,
                       EmailCodeService emailCodeService,
                       StorageService storageService) {
        this.userRepository = userRepository;
        this.loginFailRepository = loginFailRepository;
        this.emailCodeService = emailCodeService;
        this.storageService = storageService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    // ==================== 图形验证码 ====================

    /**
     * 生成图形验证码，返回 {captchaKey, captchaImage(base64)}
     */
    public Map<String, String> generateCaptcha() {
        if (!captchaRequired) {
            return Map.of("captchaRequired", "false");
        }
        CaptchaUtil.CaptchaResult result = CaptchaUtil.generate();
        String key = UUID.randomUUID().toString().replace("-", "");
        captchaCache.put(key, new CaptchaCacheEntry(result.code(), System.currentTimeMillis() + 120_000));
        // 定期清理过期key
        if (captchaCache.size() > 1000) {
            captchaCache.entrySet().removeIf(e -> System.currentTimeMillis() > e.getValue().expireTime);
        }
        return Map.of("captchaRequired", "true", "captchaKey", key, "captchaImage", result.base64Image());
    }

    public void validateCaptcha(String captchaKey, String captchaCode) {
        if (!captchaRequired) {
            return;
        }
        if (captchaKey == null || captchaCode == null || captchaCode.trim().isEmpty()) {
            throw new CaptchaValidationException("Captcha code is required");
        }

        CaptchaCacheEntry entry = captchaCache.remove(captchaKey); // 一次性使用
        if (entry == null) {
            throw new CaptchaValidationException("Captcha expired, please refresh and try again");
        }
        if (System.currentTimeMillis() > entry.expireTime) {
            throw new CaptchaValidationException("Captcha expired, please refresh and try again");
        }
        if (!entry.code.equalsIgnoreCase(captchaCode.trim())) {
            throw new CaptchaValidationException("Captcha code is incorrect");
        }
    }

    // ==================== 账号密码 + 验证码登录 ====================

    public LoginResponse loginWithPassword(LoginWithCaptchaRequest request, HttpServletRequest httpRequest) {
        // 1. 校验图形验证码
        validateCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        // 2. 查找用户
        User user = userRepository.findByUsername(request.getLogin())
                .orElseGet(() -> userRepository.findByPhone(request.getLogin())
                        .orElse(null));

        if (user == null) {
            log.warn("Login failed: account not found — {}", request.getLogin());
            throw new BadRequestException("Invalid username or password");
        }

        // 3. 检查锁定状态
        checkLocked(user.getId());

        // 4. 检查账号状态
        if (!user.getIsActive()) {
            log.warn("Login failed: account disabled — {}", request.getLogin());
            throw new BadRequestException("Account is disabled");
        }

        // 5. 校验密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordLoginFail(user.getId(), getClientIp(httpRequest));
            throw new BadRequestException("Invalid username or password");
        }

        // 6. 登录成功
        return doLogin(user);
    }

    // ==================== 邮箱验证码登录 ====================

    public LoginResponse loginWithEmailCode(EmailLoginRequest request, HttpServletRequest httpRequest) {
        // 1. 先按邮箱查找用户（用于检查锁定状态）
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (user != null) {
            // 2. 检查锁定状态（在验证码校验之前，防止绕过锁定）
            checkLocked(user.getId());

            // 3. 检查账号状态
            if (!user.getIsActive()) {
                throw new BadRequestException("Account is disabled");
            }
        }

        // 4. 校验验证码
        boolean codeValid = emailCodeService.verifyCode(request.getEmail(), request.getCode());
        if (!codeValid) {
            if (user != null) {
                // 记录登录失败（反暴力破解）
                recordLoginFail(user.getId(), getClientIp(httpRequest));
            }
            throw new BadRequestException("Email verification code is incorrect or expired");
        }

        // 5. 验证码正确但用户不存在
        if (user == null) {
            throw new BadRequestException("Invalid username or password");
        }

        // 6. 登录成功
        return doLogin(user);
    }

    // ==================== 注册 ====================

    public LoginResponse register(RegisterRequest request) {
        // 1. 唯一性校验（先检查，不消耗验证码）
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("用户名已被注册");
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new BadRequestException("手机号已被注册");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("邮箱已被注册");
        }

        // 2. 校验邮箱验证码
        boolean emailCodeValid = emailCodeService.verifyCode(request.getEmail(), request.getEmailCode());
        if (!emailCodeValid) {
            throw new BadRequestException("邮箱验证码错误或已过期，请重新获取");
        }

        // 3. 校验昵称（若前端未传，使用用户名作为默认昵称）
        String nickname = request.getNickname() != null && !request.getNickname().isBlank()
                ? XssFilter.sanitize(request.getNickname().trim())
                : XssFilter.sanitize(request.getUsername());

        // 4. 创建用户
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = User.builder()
                .username(XssFilter.sanitize(request.getUsername().trim()))
                .password(encodedPassword)
                .nickname(nickname)
                .phone(request.getPhone() != null ? XssFilter.sanitize(request.getPhone()) : null)
                .email(XssFilter.sanitize(request.getEmail().trim().toLowerCase()))
                .termsVersion("2026-07-21")
                .termsAcceptedAt(LocalDateTime.now())
                .role("user")
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("User registered: {} (email: {})", user.getUsername(), maskEmail(user.getEmail()));

        // 5. 自动登录
        return doLogin(user);
    }

    /** 邮箱脱敏用于日志输出 */
    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String name = parts[0];
        String domain = parts[1];
        if (name.length() <= 2) return name + "***@" + domain;
        return name.substring(0, 2) + "***@" + domain;
    }

    // ==================== 字段可用性检查 ====================

    /** 字段可用性检查结果 */
    public record CheckFieldResult(String field, String value, boolean available, String message) {}

    /**
     * 检查用户名/邮箱/手机号是否可用（用于注册实时提示）。
     * 限流：每 IP 每秒最多 1 次请求。
     */
    public CheckFieldResult checkFieldAvailability(String field, String value, HttpServletRequest request) {
        // 限流检查
        String ip = getClientIp(request);
        long now = System.currentTimeMillis();
        Long lastCheck = fieldCheckRateLimiter.get(ip);
        if (lastCheck != null && (now - lastCheck) < 1000) {
            throw new BadRequestException("操作过于频繁，请稍后再试");
        }
        fieldCheckRateLimiter.put(ip, now);

        // 定期清理过期限流记录
        if (fieldCheckRateLimiter.size() > 5000) {
            fieldCheckRateLimiter.entrySet().removeIf(e -> now - e.getValue() > 5000);
        }

        if (value == null || value.isBlank()) {
            return new CheckFieldResult(field, value, false, "字段不能为空");
        }

        boolean available;
        String message;

        switch (field) {
            case "username" -> {
                available = !userRepository.existsByUsername(value.trim());
                message = available ? "用户名可用" : "用户名已被注册";
            }
            case "email" -> {
                String email = value.trim().toLowerCase();
                // 先校验格式
                if (!email.matches("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")) {
                    return new CheckFieldResult(field, value, false, "邮箱格式不正确");
                }
                available = !userRepository.existsByEmail(email);
                message = available ? "邮箱可用" : "邮箱已被注册";
            }
            case "phone" -> {
                String phone = value.trim();
                if (!phone.matches("^1[3-9]\\d{9}$")) {
                    return new CheckFieldResult(field, value, false, "手机号格式不正确");
                }
                available = !userRepository.existsByPhone(phone);
                message = available ? "手机号可用" : "手机号已被注册";
            }
            default -> throw new BadRequestException("不支持的字段类型：" + field);
        }

        return new CheckFieldResult(field, value, available, message);
    }

    // ==================== 发送邮箱验证码 ====================

    /**
     * 发送邮箱验证码（需先通过图形验证码校验）
     * 每日每邮箱限5次，发送后2分钟冷却才能使用
     */
    public void sendEmailCode(EmailCodeRequest request) {
        // 1. 校验图形验证码（防止自动化批量请求）
        validateCaptcha(request.getCaptchaKey(), request.getCaptchaCode());

        // 2. 检查每日上限
        int dailyCount = emailCodeService.getDailyCount(request.getEmail());
        if (dailyCount >= emailCodeService.getMaxDailySends()) {
            throw new BadRequestException(
                    "Daily email code limit reached (" + emailCodeService.getMaxDailySends()
                            + " per day), please try again tomorrow");
        }

        // 3. 发送验证码
        emailCodeService.sendCode(request.getEmail());
    }

    // ==================== 退出登录 ====================

    public void logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New passwords do not match");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("User not found"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password must differ from current password");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        StpUtil.logout(userId);
    }

    // ==================== 内部方法 ====================

    /**
     * 执行登录：清除失败记录 → 更新登录时间 → 创建 Sa-Token
     */
    private LoginResponse doLogin(User user) {
        // 清除登录失败记录
        loginFailRepository.deleteByUserId(user.getId());

        // 更新最后登录时间
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        // Sa-Token 登录（使用 userId 作为 loginId）
        StpUtil.login(user.getId());
        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();

        log.info("User logged in: {} (id={})", user.getUsername(), user.getId());
        return LoginResponse.builder()
                .tokenName(tokenInfo.getTokenName())
                .tokenValue(tokenInfo.getTokenValue())
                .user(UserResponse.fromEntity(user, false, storageService))
                .build();
    }

    /**
     * 检查账号是否被锁定
     */
    private void checkLocked(Long userId) {
        LoginFail fail = loginFailRepository.findByUserId(userId).orElse(null);
        if (fail == null || fail.getLockedUntil() == null) return;

        if (LocalDateTime.now().isBefore(fail.getLockedUntil())) {
            long remaining = java.time.Duration.between(LocalDateTime.now(), fail.getLockedUntil()).toMinutes() + 1;
            log.warn("Login blocked: user id={} locked for {} more minutes", userId, remaining);
            throw new AccountLockedException(remaining);
        }
        // 锁定期已过，清除
        loginFailRepository.deleteByUserId(userId);
    }

    /**
     * 记录登录失败
     */
    private void recordLoginFail(Long userId, String ip) {
        LoginFail fail = loginFailRepository.findByUserId(userId)
                .orElse(LoginFail.builder()
                        .userId(userId)
                        .ipAddress(ip)
                        .failCount(0)
                        .build());

        fail.setFailCount(fail.getFailCount() + 1);
        fail.setLastFail(LocalDateTime.now());
        fail.setIpAddress(ip);

        if (fail.getFailCount() >= maxFailCount) {
            fail.setLockedUntil(LocalDateTime.now().plusMinutes(lockMinutes));
            log.warn("Account locked: user id={} locked until {}", userId, fail.getLockedUntil());
        }

        loginFailRepository.save(fail);
    }

    /**
     * 获取客户端真实IP
     * 取 X-Forwarded-For 最右侧一跳（离服务器最近的代理），防止客户端伪造。
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // 取最右侧非空IP（最近的代理地址，无法被客户端伪造）
            String[] parts = xForwardedFor.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String ip = parts[i].trim();
                if (!ip.isEmpty()) {
                    return ip;
                }
            }
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 获取指定 key 的验证码文本（仅供测试使用）。
     * 生产代码中验证码是一次性的，调用此方法不会消耗验证码。
     */
    String getCaptchaCodeForTest(String captchaKey) {
        CaptchaCacheEntry entry = captchaCache.get(captchaKey);
        return entry != null ? entry.code() : null;
    }

    private record CaptchaCacheEntry(String code, long expireTime) {
    }
}
