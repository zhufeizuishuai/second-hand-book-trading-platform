package org.example.service;


import com.baomidou.mybatisplus.extension.service.IService;
import org.example.api.po.Book;
import org.example.api.po.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface UserService extends IService<User> {
    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    ResponseEntity<?> updateProfile(Map<String, Object> userData);

    ResponseEntity<?> uploadAvatar(MultipartFile file);

    ResponseEntity<List<Book>> getUserBooks();

    ResponseEntity<?> getMe();
}
