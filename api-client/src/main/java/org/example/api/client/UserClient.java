package org.example.api.client;

import org.example.api.client.fallback.UserClientFallbackFactory;
import org.example.api.po.User;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "user-service", fallbackFactory = UserClientFallbackFactory.class)
public interface UserClient {
    @GetMapping("/api/user/findByUsername")
    User findByUsername(@RequestParam("username") String username);
    @GetMapping("/api/user/findByEmail")
    User findByEmail(@RequestParam("email") String email);
    @PostMapping("/api/user/save")
    void save(@RequestBody User user);

    @GetMapping("/api/user/getById/{id}")
    User getById(@PathVariable Long id);
}
