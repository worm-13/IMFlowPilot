package com.worm.server.controller;

import java.util.Map;

import com.worm.server.service.SessionManager;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatusController {

    private final SessionManager sessionManager;

    public StatusController(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @GetMapping("/ws/status")
    public Map<String, Integer> status() {
        return Map.of("connections", sessionManager.getSessionCount());
    }
}

