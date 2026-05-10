package org.example.dto;

import org.example.api.dto.UserDTO;

public class JwtResponse {
    private String token;
    private UserDTO user;

    // 构造方法、getter和setter
    public JwtResponse() {}

    public JwtResponse(String token, UserDTO user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public UserDTO getUser() {
        return user;
    }

    public void setUser(UserDTO user) {
        this.user = user;
    }
}