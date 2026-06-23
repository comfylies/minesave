package com.gamesaves.gamesaves.config;

import cn.dev33.satoken.stp.StpInterface;
import com.gamesaves.gamesaves.entity.User;
import com.gamesaves.gamesaves.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限与角色加载实现
 * 每次鉴权时从数据库加载用户的角色和权限列表
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    private final UserRepository userRepository;

    public StpInterfaceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 当前项目按角色控制权限，不单独维护权限码
        // admin 拥有全部权限，user 拥有基本权限
        User user = userRepository.findById(Long.parseLong(loginId.toString())).orElse(null);
        if (user == null) return Collections.emptyList();

        List<String> permissions = new ArrayList<>();
        permissions.add("user:read");
        permissions.add("comment:create");

        if ("admin".equals(user.getRole())) {
            permissions.add("user:manage");
            permissions.add("game:manage");
            permissions.add("article:manage");
            permissions.add("tag:manage");
        }
        return permissions;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = userRepository.findById(Long.parseLong(loginId.toString())).orElse(null);
        if (user == null) return Collections.emptyList();
        return Collections.singletonList(user.getRole());
    }
}
