package org.example.dto;

public class RegisterRequest {
    private String username;
    private String password;
    private String email;
    private String campus;

    // 构造方法、getter和setter
    public RegisterRequest() {}

    public RegisterRequest(String username, String password, String email, String campus) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.campus = campus;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCampus() {
        return campus;
    }

    public void setCampus(String campus) {
        this.campus = campus;
    }
}