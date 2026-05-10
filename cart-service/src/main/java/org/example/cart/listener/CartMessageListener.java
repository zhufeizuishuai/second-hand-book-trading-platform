package org.example.cart.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.api.config.RabbitMQConfig;
import org.example.api.dto.CartCleanupMessage;
import org.example.cart.service.CartItemService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartMessageListener {

    private final CartItemService cartItemService;

    @RabbitListener(queues = RabbitMQConfig.CART_CLEANUP_QUEUE)
    public void handleCartCleanup(CartCleanupMessage message) {
        log.info("收到购物车清理消息: userId={}, bookIds={}", message.getUserId(), message.getBookIds());
        try {
            cartItemService.deleteByUserIdAndBookIds(message.getUserId(), message.getBookIds());
            log.info("购物车清理完成: userId={}", message.getUserId());
        } catch (Exception e) {
            log.error("购物车清理失败: userId={}, error={}", message.getUserId(), e.getMessage(), e);
        }
    }
}
