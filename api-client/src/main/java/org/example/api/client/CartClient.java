package org.example.api.client;

import org.example.api.client.fallback.CartClientFallbackFactory;
import org.example.api.po.CartItem;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
@FeignClient(name = "cart-service", fallbackFactory = CartClientFallbackFactory.class)

public interface CartClient {

        @DeleteMapping("/api/cart/items")
        void clearCart();


}
