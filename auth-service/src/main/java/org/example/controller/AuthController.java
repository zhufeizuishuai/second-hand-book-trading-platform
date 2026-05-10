package org.example.controller;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.RequiredArgsConstructor;
import org.example.api.client.UserClient;
import org.example.api.dto.UserDTO;
import org.example.api.po.User;
import org.example.api.security.JwtUtils;
import org.example.dto.JwtResponse;
import org.example.dto.LoginRequest;
import org.example.dto.RegisterRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {


    private final AuthenticationManager authenticationManager;

    private final UserClient userClient;

    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;
    @SentinelResource(
            value = "login",
            blockHandler = "loginBlockHandler",
            fallback = "loginFallback"
    )
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getUsername(),
                        loginRequest.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        Optional<User> userOptional = Optional.ofNullable(userClient.findByUsername(loginRequest.getUsername()));
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            UserDTO userDTO = new UserDTO(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getCampus(),
                    user.getAvatar(),
                    user.getRating()
            );
            try {
                stringRedisTemplate.opsForValue().set("user:" + userDTO.getId(), JSONUtil.toJsonStr(userDTO));
            } catch (Exception e) {
                System.err.println("Redis 缓存失败，不影响登录: " + e.getMessage());
            }
            return ResponseEntity.ok(new JwtResponse(jwt, userDTO));
        }
        UserDTO userDTO = new UserDTO();
        return ResponseEntity.badRequest().build();
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest) {
        // 检查用户名是否已存在
        if (userClient.findByUsername(registerRequest.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Username is already taken!");
        }

        // 检查邮箱是否已存在
        if (userClient.findByEmail(registerRequest.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Error: Email is already in use!");
        }

        // 创建新用户
        User user = new User(
                registerRequest.getUsername(),
                passwordEncoder.encode(registerRequest.getPassword()),
                registerRequest.getEmail()
        );
        user.setCampus(registerRequest.getCampus());
        userClient.save(user);
        return ResponseEntity.ok("User registered successfully!");
    }
    // 限流/熔断后的处理
    public ResponseEntity<?> loginBlockHandler(LoginRequest request, BlockException ex) {
        return ResponseEntity.status(429)
                .body(Map.of("code", 429, "message", "系统繁忙，请稍后再试"));
    }

    // 业务异
    public ResponseEntity<?> loginFallback(LoginRequest request, Throwable ex) {
        return ResponseEntity.status(503)
                .body(Map.of("code", 503, "message", "服务暂时不可用"));
    }

}