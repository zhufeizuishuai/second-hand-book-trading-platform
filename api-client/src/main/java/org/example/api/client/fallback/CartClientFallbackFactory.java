package org.example.api.client.fallback;

import lombok.extern.slf4j.Slf4j;
import org.example.api.client.CartClient;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CartClientFallbackFactory implements FallbackFactory<CartClient> {

    @Override
    public CartClient create(Throwable cause) {
        log.error("cart-service 调用失败，触发 Sentinel 降级", cause);
        return () -> {};
    }
}
