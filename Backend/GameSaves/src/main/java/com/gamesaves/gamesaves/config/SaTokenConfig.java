package com.gamesaves.gamesaves.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由拦截鉴权配置
 *
 * 认证策略：
 * - /api/auth/**     — 公开（登录、注册、验证码、邮箱验证码）
 * - GET /api/games/**    — 公开（浏览游戏），POST/PUT/DELETE 需登录
 * - GET /api/articles/** — 公开（浏览存档），POST/PUT/DELETE 需登录
 * - /api/files/**    — 公开（浏览文件、下载）
 * - GET /api/comments/** — 公开（浏览批注），POST/DELETE 需登录
 * - /api/site-settings/** — 公开（站点设置，如首页背景图）
 * - /api/admin/**    — 需登录 + admin 权限（由 @SaCheckPermission 控制）
 * - 其他 /api/**     — 需登录
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /**
     * 如果当前请求已登录，刷新 token 活跃时间。
     * 用于公开接口（前台浏览），避免用户一直使用前台但 token 因 active-timeout 过期，
     * 导致进入后台时被拦截。
     */
    private void refreshIfLogin() {
        try {
            Object loginId = StpUtil.getLoginIdByToken(StpUtil.getTokenValue());
            if (loginId != null) {
                StpUtil.updateLastActiveToNow();
            }
        } catch (Exception ignored) {
            // 未登录，无需刷新
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // 公开接口放行前，若已登录则刷新活跃时间，避免前台浏览导致 token 过期
                    SaRouter
                        .match("/api/auth/**").stop()
                        .matchMethod("GET").match("/api/games/**", r -> refreshIfLogin()).stop()
                        .matchMethod("GET").match("/api/articles/**", r -> refreshIfLogin()).stop()
                        .match("/api/files/**", r -> refreshIfLogin()).stop()
                        // /api/users/** GET 公开（浏览用户），PUT 需登录
                        .matchMethod("GET").match("/api/users/**", r -> refreshIfLogin()).stop()
                        // 批注：GET 公开（stop），POST/DELETE 需登录走下一级 checkLogin
                        .matchMethod("GET").match("/api/comments/**", r -> refreshIfLogin()).stop()
                        // 站点设置：全部公开
                        .match("/api/site-settings/**", r -> refreshIfLogin()).stop()
                        // 其他 /api/** 需要登录，并刷新活跃时间
                        .match("/api/**", r -> {
                            refreshIfLogin();
                            StpUtil.checkLogin();
                        });
                }))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/storage/**");
    }
}
