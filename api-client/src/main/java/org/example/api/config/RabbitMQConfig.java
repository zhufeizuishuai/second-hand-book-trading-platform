package org.example.api.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // 交换机
    public static final String ORDER_EXCHANGE = "order.exchange";
    // 队列
    public static final String CART_CLEANUP_QUEUE = "cart.cleanup.queue";
    // 路由键
    public static final String CART_CLEANUP_ROUTING_KEY = "cart.cleanup";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE);
    }

    @Bean
    public Queue cartCleanupQueue() {
        return new Queue(CART_CLEANUP_QUEUE, true);
    }

    @Bean
    public Binding cartCleanupBinding() {
        return BindingBuilder
                .bind(cartCleanupQueue())
                .to(orderExchange())
                .with(CART_CLEANUP_ROUTING_KEY);


    }
}
