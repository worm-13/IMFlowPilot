package com.imflowpilot.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 简单控制器：提供健康检查
 */
@RestController
public class ChatController {
    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
