package com.worm.server.controller;

import com.worm.server.dto.CreateUserRequest;
import com.worm.server.dto.CreateUserResponse;
import com.worm.server.model.User;
import com.worm.server.service.UserService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/user/create")
    public CreateUserResponse create(@RequestBody CreateUserRequest request) {
        User user = userService.createUser(request.getNickname());
        return new CreateUserResponse(user.getId(), user.getNickname());
    }
}

