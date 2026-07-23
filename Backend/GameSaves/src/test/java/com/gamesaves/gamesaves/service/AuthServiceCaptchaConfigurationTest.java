package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.config.DataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(properties = "app.login.captcha-required=false")
class AuthServiceCaptchaConfigurationTest {

    @Autowired
    private AuthService authService;

    @MockBean
    private CleanupScheduler cleanupScheduler;

    @MockBean
    private DataInitializer dataInitializer;

    @Test
    void localProfileSkipsMissingGraphicalCaptcha() {
        assertDoesNotThrow(() -> authService.validateCaptcha(null, null));
    }

    @Test
    void localProfileAdvertisesThatGraphicalCaptchaIsNotRequired() {
        assertThat(authService.generateCaptcha())
                .containsEntry("captchaRequired", "false")
                .doesNotContainKeys("captchaKey", "captchaImage");
    }
}
