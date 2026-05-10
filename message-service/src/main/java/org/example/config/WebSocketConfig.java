package org.example.config;

import jakarta.annotation.PostConstruct;
import org.example.websocket.ChatWebSocketServer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

@Configuration
public class WebSocketConfig {

    // 你的原本代码 → 保留
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    // 新增：注入 ApplicationContext 到 WebSocket，解决无法获取 Bean 的问题
    private final ApplicationContext applicationContext;

    public WebSocketConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        ChatWebSocketServer.setApplicationContext(applicationContext);
    }
}