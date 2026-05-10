package org.example.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.example.api.po.Order;
import org.example.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    // 创建订单
    @SentinelResource(value = "createOrder", blockHandler = "createOrderBlock")
    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> orderData) {
        return orderService.createOrder(orderData);
    }

    // 获取用户订单列表
    @GetMapping("/user")
    public ResponseEntity<List<Order>> getUserOrders() {
        return orderService.getOrders();
    }

    // 获取订单详情
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderDetails(@PathVariable Long id) {
        return orderService.getOrderDetails(id);
    }

    // 更新订单状态
    @PutMapping("/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable Long id, @RequestParam String status) {
        return orderService.updateOrderStatus(id, status);
    }

    // 取消订单
    @PutMapping("/{id}/cancel")
    public ResponseEntity<Order> cancelOrder(@PathVariable Long id) {
        return orderService.cancelOrder(id);
    }

    // === Sentinel block handlers ===
    public ResponseEntity<?> createOrderBlock(Map<String, Object> orderData, BlockException ex) {
        return ResponseEntity.status(429).body(Map.of("code", 429, "message", "订单接口繁忙，请稍后再试"));
    }
}