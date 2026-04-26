package com.imflowpilot.backend.config;

import com.imflowpilot.backend.websocket.WebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 注册配置：注册 /ws 端点
 *
 * 改为直接 new WebSocketHandler() 注册，避免依赖注入失败导致找不到 Bean 的问题。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 直接创建 handler 实例，避免依赖自动装配问题
        registry.addHandler(new WebSocketHandler(), "/ws")
                .setAllowedOrigins("http://localhost:5173", "http://localhost:3000", "*");
    }
}
