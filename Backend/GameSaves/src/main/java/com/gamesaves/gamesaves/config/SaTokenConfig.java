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
 * - /api/games/**    — 公开（浏览游戏）
 * - /api/articles/** — 公开（浏览存档）
 * - /api/users/register、/api/users/login — 公开
 * - /api/files/**    — 公开（浏览文件）
 * - GET /api/comments/** — 公开（浏览批注），POST/DELETE 需登录
 * - /api/admin/**    — 需登录 + admin 权限（由 @SaCheckPermission 控制）
 * - 其他 /api/**     — 需登录
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
                    // 公开接口放行（注意：必须链式调用，不可拆成多条独立语句）
                    SaRouter
                        .match("/api/auth/**").stop()
                        .match("/api/games/**").stop()
                        .match("/api/users/register").stop()
                        .match("/api/users/login").stop()
                        .match("/api/articles/**").stop()
                        .match("/api/files/**").stop()
                        // 批注：GET 公开（stop），POST/DELETE 需登录走下一级 checkLogin
                        .matchMethod("GET").match("/api/comments/**").stop()
                        // 其他 /api/** 需要登录
                        .match("/api/**", r -> StpUtil.checkLogin());
                }))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/storage/**");
    }
}
