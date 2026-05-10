package org.example.api.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Date;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String campus;
    private String avatar;
    private double rating;
    private LocalDateTime createTime;

    // 构造方法、getter和setter
    public UserDTO() {}

    public UserDTO(Long id, String username, String email, String campus, String avatar, double rating) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.campus = campus;
        this.avatar = avatar;
        this.rating = rating;
        this.createTime = LocalDateTime.now();
    }

}