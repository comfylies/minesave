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
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
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
 * 认证服务
 * - 账号密码 + 图形验证码登录
 * - 邮箱验证码登录
 * - 用户注册
 * - 登录失败计数 & 账号锁定
 * - 图形验证码生成 & 校验
 */
@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    // 图形验证码缓存：captchaKey → code
    private final ConcurrentHashMap<String, CaptchaCacheEntry> captchaCache = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final LoginFailRepository loginFailRepository;
    private final EmailCodeService emailCodeService;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${app.login.max-fail-count:5}")
    private int maxFailCount;

    @Value("${app.login.lock-minutes:30}")
    private int lockMinutes;

    @Value("${app.login.captcha-enabled:true}")
    private boolean captchaEnabled;

    public AuthService(UserRepository userRepository,
                       LoginFailRepository loginFailRepository,
                       EmailCodeService emailCodeService) {
        this.userRepository = userRepository;
        this.loginFailRepository = loginFailRepository;
        this.emailCodeService = emailCodeService;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    // ==================== 图形验证码 ====================

    /**
     * 生成图形验证码，返回 {captchaKey, captchaImage(base64)}
     */
    public Map<String, String> generateCaptcha() {
        CaptchaUtil.CaptchaResult result = CaptchaUtil.generate();
        String key = UUID.randomUUID().toString().replace("-", "");
        captchaCache.put(key, new CaptchaCacheEntry(result.code(), System.currentTimeMillis() + 120_000));
        // 定期清理过期key
        if (captchaCache.size() > 1000) {
            captchaCache.entrySet().removeIf(e -> System.currentTimeMillis() > e.getValue().expireTime);
        }
        return Map.of("captchaKey", key, "captchaImage", result.base64Image());
    }

    private void validateCaptcha(String captchaKey, String captchaCode) {
        if (!captchaEnabled) return; // 开发/测试阶段可关闭

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
            throw new BadRequestException("Account not found");
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
            throw new BadRequestException("Incorrect password");
        }

        // 6. 登录成功
        return doLogin(user);
    }

    // ==================== 邮箱验证码登录 ====================

    public LoginResponse loginWithEmailCode(EmailLoginRequest request) {
        // 1. 校验验证码
        if (!emailCodeService.verifyCode(request.getEmail(), request.getCode())) {
            throw new BadRequestException("Email verification code is incorrect or expired");
        }

        // 2. 查找用户（按邮箱）
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No account associated with this email"));

        // 3. 检查锁定状态
        checkLocked(user.getId());

        // 4. 检查账号状态
        if (!user.getIsActive()) {
            throw new BadRequestException("Account is disabled");
        }

        // 5. 登录成功
        return doLogin(user);
    }

    // ==================== 注册 ====================

    public LoginResponse register(RegisterRequest request, String captchaKey, String captchaCode) {
        // 1. 校验图形验证码
        validateCaptcha(captchaKey, captchaCode);

        // 2. 唯一性校验
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already exists: " + request.getUsername());
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new BadRequestException("Phone already registered: " + request.getPhone());
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered: " + request.getEmail());
        }

        // 3. 创建用户
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = User.builder()
                .username(XssFilter.sanitize(request.getUsername()))
                .password(encodedPassword)
                .nickname(request.getNickname() != null
                        ? XssFilter.sanitize(request.getNickname())
                        : XssFilter.sanitize(request.getUsername()))
                .phone(request.getPhone() != null ? XssFilter.sanitize(request.getPhone()) : null)
                .email(request.getEmail() != null ? XssFilter.sanitize(request.getEmail()) : null)
                .role("user")
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("User registered: {}", user.getUsername());

        // 4. 自动登录
        return doLogin(user);
    }

    // ==================== 发送邮箱验证码 ====================

    public void sendEmailCode(EmailCodeRequest request) {
        emailCodeService.sendCode(request.getEmail());
    }

    // ==================== 退出登录 ====================

    public void logout() {
        if (StpUtil.isLogin()) {
            StpUtil.logout();
        }
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
                .user(UserResponse.fromEntity(user))
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
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private record CaptchaCacheEntry(String code, long expireTime) {
    }
}
