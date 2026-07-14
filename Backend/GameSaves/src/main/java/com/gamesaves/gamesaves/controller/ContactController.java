package com.gamesaves.gamesaves.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.gamesaves.gamesaves.dto.request.ContactCreateRequest;
import com.gamesaves.gamesaves.dto.response.ApiResponse;
import com.gamesaves.gamesaves.dto.response.ContactResponse;
import com.gamesaves.gamesaves.service.ContactService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
@SaCheckLogin
public class ContactController {

    private static final Logger log = LoggerFactory.getLogger(ContactController.class);

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    /**
     * 提交联系留言（需登录）
     */
    @PostMapping
    public ApiResponse<ContactResponse> submit(
            @Valid @RequestBody ContactCreateRequest request,
            HttpServletRequest httpRequest) {
        Long userId = StpUtil.getLoginIdAsLong();
        String ip = getClientIp(httpRequest);
        ContactResponse response = contactService.submit(request, userId, ip);
        return ApiResponse.success("留言提交成功，感谢您的反馈！", response);
    }

    /**
     * 获取待处理留言数量（导航栏红点用，仅管理员有意义）
     */
    @GetMapping("/pending-count")
    public ApiResponse<Long> getPendingCount() {
        long count = contactService.countPending();
        return ApiResponse.success(count);
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
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
}
