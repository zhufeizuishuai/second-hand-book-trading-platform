package org.example.gatewayservice;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.Map;

@Configuration
public class SentinelGatewayConfig {

    @PostConstruct
    public void init() {
        GatewayCallbackManager.setBlockHandler((exchange, t) ->
            ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                    "code", 429,
                    "message", "请求过于频繁，请稍后再试",
                    "blockedBy", t.getClass().getSimpleName()
                ))
        );
    }
}
