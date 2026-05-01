package com.worm.server.service;

import com.worm.server.mapper.UserMapper;
import com.worm.server.model.User;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User createUser(String nickname) {
        if (!StringUtils.hasText(nickname)) {
            throw new IllegalArgumentException("nickname 不能为空");
        }

        User user = new User();
        user.setId("user_" + UUID.randomUUID().toString().replace("-", ""));
        user.setNickname(nickname.trim());
        user.setCreatedAt(System.currentTimeMillis());
        userMapper.insert(user);
        return user;
    }

    public User findById(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        return userMapper.findById(id);
    }
}

