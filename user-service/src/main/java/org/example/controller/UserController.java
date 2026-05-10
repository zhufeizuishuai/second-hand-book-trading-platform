package org.example.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.example.api.po.Book;
import org.example.api.po.User;
import org.example.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;
    @SentinelResource(value = "updateProfile", blockHandler = "updateProfileBlock")
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> userData) {
        return userService.updateProfile(userData);
    }
    @PutMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestBody MultipartFile file) {
        return userService.uploadAvatar(file);
    }
    @GetMapping("/books")
    public ResponseEntity<List<Book>> getUserBooks() {
        return userService.getUserBooks();
    }
    @SentinelResource(value = "getMe", blockHandler = "getMeBlock")
    @GetMapping("/me")
    public ResponseEntity<?> getMe() {
        return userService.getMe();
    }

    @GetMapping("/findByUsername")
    public User findByUsername(@RequestParam String username) {
        return userService.findByUsername(username)
                .orElse(null);
    }
    @GetMapping("/findByEmail")
    public User findByEmail(@RequestParam String email) {
        return userService.findByEmail(email)
                .orElse(null);
    }

    @PostMapping( "/save")
    public void  save(@RequestBody User user) {
         userService.save(user);
    }

    @GetMapping("/getById/{id}")
    public User getById(@PathVariable Integer id) {
        return userService.getById(id);
    }

    // === Sentinel block handlers ===
    public ResponseEntity<?> updateProfileBlock(Map<String, Object> userData, BlockException ex) {
        return ResponseEntity.status(429).body(Map.of("code", 429, "message", "用户资料更新繁忙，请稍后再试"));
    }

    public ResponseEntity<?> getMeBlock(BlockException ex) {
        return ResponseEntity.status(429).body(Map.of("code", 429, "message", "用户信息查询繁忙，请稍后再试"));
    }
}