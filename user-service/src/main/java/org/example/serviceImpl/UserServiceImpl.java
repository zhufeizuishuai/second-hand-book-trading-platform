package org.example.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.example.api.client.BookClient;
import org.example.api.po.Book;
import org.example.api.po.User;
import org.example.mapper.UserMapper;
import org.example.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final BookClient bookClient;

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(baseMapper.selectOne(new QueryWrapper<User>()
                .eq("username", username)));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(baseMapper.selectOne(new QueryWrapper<User>()
                .eq("email", email)));
    }

    // 获取当前登录用户
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            return findByUsername(userDetails.getUsername()).orElse(null);
        }
        return null;
    }

    @Override
    public ResponseEntity<?> updateProfile(Map<String, Object> userData) {
        try {
            User currentUser = getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "用户未登录"));
            }

            // 更新用户信息（全部安全处理，绝不空指针）
            if (userData.containsKey("name")) {
                currentUser.setUsername(toString(userData.get("name")));
            }
            if (userData.containsKey("email")) {
                currentUser.setEmail(toString(userData.get("email")));
            }
            if (userData.containsKey("phone")) {
                currentUser.setPhone(toString(userData.get("phone")));
            }
            if (userData.containsKey("bio")) {
                currentUser.setBio(toString(userData.get("bio")));
            }
            if (userData.containsKey("school")) {
                currentUser.setCampus(toString(userData.get("school")));
            }
            if (userData.containsKey("tradeMethods")) {
                currentUser.setTradeMethods(toString(userData.get("tradeMethods")));
            }

            updateById(currentUser);
            return ResponseEntity.ok(currentUser);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "更新用户信息失败"));
        }
    }

    // 🔥 安全转字符串工具方法（防止 null 调用 toString()）
    private String toString(Object obj) {
        return obj == null ? null : obj.toString();
    }

    @Override
    public ResponseEntity<?> uploadAvatar(MultipartFile file) {
        try {
            User currentUser = getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "用户未登录"));
            }
            
            // 保存文件
            String uploadDir = "uploads/avatars/";
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            String fileName = currentUser.getId() + "_" + System.currentTimeMillis() + ".jpg";
            String filePath = uploadDir + fileName;
            file.transferTo(new File(filePath));
            
            // 更新用户头像
            String avatarUrl = "/uploads/avatars/" + fileName;
            currentUser.setAvatar(avatarUrl);
            baseMapper.updateById(currentUser);
            
            Map<String, String> result = new HashMap<>();
            result.put("avatar", avatarUrl);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "上传头像失败"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "上传头像失败"));
        }
    }

    @Override
    public ResponseEntity<List<Book>> getUserBooks() {
        try {
            User currentUser = getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body((List<Book>) Map.of("error", "用户未登录"));
            }
            ResponseEntity<List<Book>> books = bookClient.getUserBooks(currentUser.getId());

            return ResponseEntity.ok(books.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body((List<Book>) Map.of("error", "获取书籍列表失败"));
        }
    }

    @Override
    public ResponseEntity<?> getMe() {

        return ResponseEntity.ok(getById(getCurrentUser().getId()));
    }
}