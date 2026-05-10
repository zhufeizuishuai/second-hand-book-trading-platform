package org.example.cart.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.api.po.CartItem;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CartItemService extends IService<CartItem> {
    ResponseEntity<?> getCartItems();

    ResponseEntity<?> addToCart(CartItem cartItem);

    Optional<CartItem> findByUserIdAndBookId(Long currentUserId, Long id);

    ResponseEntity<?> clearCart();

    ResponseEntity<Map<String, Object>> updateQuantity(Long id, Integer quantity);

    ResponseEntity<?> deleteById(Long id);

    void deleteByUserIdAndBookIds(Long userId, List<Long> bookIds);
}
