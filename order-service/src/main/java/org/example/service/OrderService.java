package org.example.service;


import com.baomidou.mybatisplus.extension.service.IService;
import org.example.api.po.Order;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

public interface OrderService extends IService<Order> {
    ResponseEntity<?> createOrder(Map<String, Object> orderData);

    ResponseEntity<List<Order>> getOrders();

    ResponseEntity<Order> updateOrderStatus(Long id, String status);

    ResponseEntity<Order> getOrderDetails(Long id);

    ResponseEntity<Order> cancelOrder(Long id);
}
