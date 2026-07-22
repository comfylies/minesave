package com.gamesaves.gamesaves.config;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class MessageStompAuthInterceptor implements ChannelInterceptor {
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            Object loginId = token == null ? null : StpUtil.getLoginIdByToken(token);
            if (loginId == null) throw new IllegalArgumentException("WebSocket authentication is required");
            String name = String.valueOf(loginId);
            accessor.setUser((Principal) () -> name);
        }
        return message;
    }
}
