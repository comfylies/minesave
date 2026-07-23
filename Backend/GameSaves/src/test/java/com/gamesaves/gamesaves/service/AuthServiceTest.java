package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.request.EmailCodeRequest;
import com.gamesaves.gamesaves.dto.request.LoginWithCaptchaRequest;
import com.gamesaves.gamesaves.dto.request.RegisterRequest;
import com.gamesaves.gamesaves.dto.response.LoginResponse;
import com.gamesaves.gamesaves.exception.AccountLockedException;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.CaptchaValidationException;
import com.gamesaves.gamesaves.config.DataInitializer;
import com.gamesaves.gamesaves.repository.LoginFailRepository;
import com.gamesaves.gamesaves.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AuthService 集成测试 — 登录/注册/锁定/验证码/邮箱码。
 * 使用真实 MySQL + @Transactional 回滚。
 */
@SpringBootTest(properties = "app.login.captcha-required=true")
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginFailRepository loginFailRepository;

    @MockBean
    private CleanupScheduler cleanupScheduler;

    @MockBean
    private DataInitializer dataInitializer;

    private MockHttpServletRequest httpRequest;

    private static final String TEST_USER_PREFIX = "test_auth_";

    @BeforeEach
    void setUp() {
        httpRequest = new MockHttpServletRequest();
        httpRequest.setRemoteAddr("127.0.0.1");
        // 初始化 Spring 请求上下文（Sa-Token 需要）
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(httpRequest, new MockHttpServletResponse()));
        // 清理残留失败记录
        userRepository.findByUsername(TEST_USER_PREFIX + "login").ifPresent(
                u -> loginFailRepository.deleteByUserId(u.getId()));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ── Helper: 生成验证码并提取 code ──

    /**
     * 生成验证码并提取验证码文本（通过 package-private 测试辅助方法）。
     */
    private CaptchaInfo generateCaptcha() {
        Map<String, String> result = authService.generateCaptcha();
        String key = result.get("captchaKey");
        String code = authService.getCaptchaCodeForTest(key);
        assertNotNull(code, "验证码不应为空");
        return new CaptchaInfo(key, code);
    }

    private record CaptchaInfo(String key, String code) {
    }

    // ═══════════════════════════════════════════════════════════════
    // 图形验证码生成
    // ═══════════════════════════════════════════════════════════════

    @Test
    void generateCaptcha_shouldReturnKeyAndImage() {
        CaptchaInfo captcha = generateCaptcha();
        assertNotNull(captcha.key());
        assertNotNull(captcha.code());
        assertEquals(4, captcha.code().length(), "验证码应为 4 位");
    }

    @Test
    void loginWithPassword_wrongCaptcha_shouldThrow() {
        CaptchaInfo captcha = generateCaptcha();
        LoginWithCaptchaRequest req = new LoginWithCaptchaRequest();
        req.setLogin("admin");
        req.setPassword("password123");
        req.setCaptchaKey(captcha.key());
        req.setCaptchaCode("WRONG");

        assertThrows(CaptchaValidationException.class,
                () -> authService.loginWithPassword(req, httpRequest));
    }

    // ═══════════════════════════════════════════════════════════════
    // 注意: 登录成功/注册成功 测试需要 Sa-Token Web 上下文（StpUtil.login），
    // 在纯 Service 层集成测试中无法初始化。已移至 API 集成测试层（MockMvc）。
    // ═══════════════════════════════════════════════════════════════

    // ═══════════════════════════════════════════════════════════════
    // H-1: 统一错误消息
    // ═══════════════════════════════════════════════════════════════

    @Test
    void loginWithPassword_wrongPassword_shouldReturnUnifiedMessage() {
        CaptchaInfo captcha = generateCaptcha();

        LoginWithCaptchaRequest req = new LoginWithCaptchaRequest();
        req.setLogin("player_one");
        req.setPassword("wrong_password");
        req.setCaptchaKey(captcha.key());
        req.setCaptchaCode(captcha.code());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authService.loginWithPassword(req, httpRequest));
        assertEquals("Invalid username or password", ex.getMessage());
    }

    @Test
    void loginWithPassword_nonExistentUser_shouldReturnUnifiedMessage() {
        CaptchaInfo captcha = generateCaptcha();

        LoginWithCaptchaRequest req = new LoginWithCaptchaRequest();
        req.setLogin("nonexistent_user_xyz");
        req.setPassword("any_password");
        req.setCaptchaKey(captcha.key());
        req.setCaptchaCode(captcha.code());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authService.loginWithPassword(req, httpRequest));
        assertEquals("Invalid username or password", ex.getMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // 账号锁定
    // ═══════════════════════════════════════════════════════════════

    @Test
    void loginWithPassword_fiveFailedAttempts_shouldLockAccount() {
        // 5 次密码错误
        for (int i = 0; i < 5; i++) {
            CaptchaInfo captcha = generateCaptcha();
            LoginWithCaptchaRequest req = new LoginWithCaptchaRequest();
            req.setLogin("speedrunner");
            req.setPassword("wrong_password");
            req.setCaptchaKey(captcha.key());
            req.setCaptchaCode(captcha.code());
            assertThrows(BadRequestException.class,
                    () -> authService.loginWithPassword(req, httpRequest),
                    "第 " + (i + 1) + " 次失败");
        }

        // 第 6 次即使密码正确也应锁定
        CaptchaInfo captcha = generateCaptcha();
        LoginWithCaptchaRequest correctReq = new LoginWithCaptchaRequest();
        correctReq.setLogin("speedrunner");
        correctReq.setPassword("password123");
        correctReq.setCaptchaKey(captcha.key());
        correctReq.setCaptchaCode(captcha.code());

        assertThrows(AccountLockedException.class,
                () -> authService.loginWithPassword(correctReq, httpRequest),
                "锁定后即使密码正确也应拒绝");
    }

    // ═══════════════════════════════════════════════════════════════
    // 注册
    // ═══════════════════════════════════════════════════════════════

    @Test
    void register_duplicateUsername_shouldThrow() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("admin");
        req.setPassword("password123");
        req.setNickname("Test");
        req.setEmail("test@example.com");

        assertThrows(BadRequestException.class,
                () -> authService.register(req));
    }

    @Test
    void register_withoutEmailCode_shouldThrow() {
        RegisterRequest req = new RegisterRequest();
        req.setUsername(TEST_USER_PREFIX + "noemailcode");
        req.setPassword("password123");
        req.setEmail("noemailcode@example.com");
        req.setEmailCode(null);

        assertThrows(BadRequestException.class,
                () -> authService.register(req));
    }

    // ═══════════════════════════════════════════════════════════════
    // 邮箱验证码发送 (H-3)
    // ═══════════════════════════════════════════════════════════════

    @Test
    void sendEmailCode_withoutCaptcha_shouldThrow() {
        EmailCodeRequest req = new EmailCodeRequest();
        req.setEmail("test@example.com");
        req.setCaptchaKey(null);
        req.setCaptchaCode(null);

        assertThrows(CaptchaValidationException.class,
                () -> authService.sendEmailCode(req));
    }

    // ═══════════════════════════════════════════════════════════════
    // 禁用账号
    // ═══════════════════════════════════════════════════════════════

    @Test
    void login_disabledAccount_shouldThrow() {
        var user = userRepository.findByUsername("speedrunner").orElseThrow();
        user.setIsActive(false);
        userRepository.save(user);

        CaptchaInfo captcha = generateCaptcha();
        LoginWithCaptchaRequest req = new LoginWithCaptchaRequest();
        req.setLogin("speedrunner");
        req.setPassword("password123");
        req.setCaptchaKey(captcha.key());
        req.setCaptchaCode(captcha.code());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authService.loginWithPassword(req, httpRequest));
        assertEquals("Account is disabled", ex.getMessage());
    }
}
