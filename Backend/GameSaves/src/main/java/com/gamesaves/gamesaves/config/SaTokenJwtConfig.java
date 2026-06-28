package com.gamesaves.gamesaves.config;

import cn.dev33.satoken.jwt.StpLogicJwtForStateless;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token JWT 无状态模式配置。
 *
 * <p>sa-token-jwt 插件不提供 Spring Boot 自动配置，需手动注册
 * {@link StpLogicJwtForStateless} 替换默认的 {@link StpLogic}。
 *
 * <p>关键区别：
 * <ul>
 *   <li>{@code StpLogicJwtForSimple} — JWT 格式但<strong>仍维护服务端会话</strong>，重启丢登录</li>
 *   <li>{@code StpLogicJwtForStateless} — <strong>完全无状态</strong>，纯靠 JWT 签名验证，重启无感</li>
 * </ul>
 */
@Configuration
public class SaTokenJwtConfig {

    @Bean
    public StpLogic stpLogic() {
        return new StpLogicJwtForStateless();
    }
}
