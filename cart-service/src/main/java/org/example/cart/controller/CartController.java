package org.example.cart.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.example.api.po.CartItem;
import org.example.cart.service.CartItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/cart")
public class CartController {

    @Autowired
    private CartItemService cartItemService;

    // 获取购物车商品列表
    @GetMapping("/items")
    public ResponseEntity<?> getCartItems() {
        return cartItemService.getCartItems();
    }

    // 添加商品到购物车
    @SentinelResource(value = "addToCart", blockHandler = "addToCartBlock")
    @PostMapping("/items")
    public ResponseEntity<?> addToCart(@RequestBody CartItem cartItem) {
        return cartItemService.addToCart(cartItem);
    }

    // 更新购物车商品数量
    @PutMapping("/items/{id}/quantity")
    public ResponseEntity<Map<String, Object>> updateQuantity(@PathVariable Long id, @RequestBody Map<String, Integer> request) {
        return cartItemService.updateQuantity(id, request.get("quantity"));
    }

    // 从购物车移除商品
    @DeleteMapping("/items/{id}")
    public ResponseEntity<?> removeFromCart(@PathVariable Long id) {

        return cartItemService.deleteById(id);
    }

    // 清空购物车
    @DeleteMapping("/items")
    public ResponseEntity<?> clearCart() {
        return cartItemService.clearCart();
    }


    @GetMapping("/items/user/{userId}")
    public List<CartItem> getCartByUserId(@PathVariable Long userId) {
        return cartItemService.lambdaQuery()
                .eq(CartItem::getUserId, userId)
                .list();
    }

    // === Sentinel block handlers ===
    public ResponseEntity<?> addToCartBlock(CartItem cartItem, BlockException ex) {
        return ResponseEntity.status(429).body(Map.of("code", 429, "message", "购物车接口繁忙，请稍后再试"));
    }
}