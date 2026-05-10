package org.example.api.po;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("users")
public class User {

    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名 非空+唯一
     */
    @TableField(value = "username", condition = SqlCondition.EQUAL)
    private String username;

    /**
     * 密码 非空
     */
    @TableField("password")
    private String password;

    /**
     * 邮箱 非空+唯一
     */
    @TableField("email")
    private String email;

    /**
     * 手机号码
     */
    @TableField("phone")
    private String phone;

    /**
     * 校区
     */
    @TableField("campus")
    private String campus;

    /**
     * 头像
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 个人简介
     */
    @TableField("bio")
    private String bio;

    /**
     * 评分
     */
    @TableField("rating")
    private double rating;
    
    /**
     * 交易次数
     */
    @TableField("trade_count")
    private int tradeCount;
    
    /**
     * 关注者数量
     */
    @TableField("follower_count")
    private int followerCount;
    
    /**
     * 交易方式
     */
    @TableField("trade_methods")
    private String tradeMethods;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    // ==================== 关联集合：MyBatis-Plus 不自动维护关联关系，仅用于业务 ====================
    /**
     * 发布的书籍
     */
    @TableField(exist = false)
    @JsonIgnore
    private List<Book> books;

    /**
     * 订单
     */
    @TableField(exist = false)
    @JsonIgnore
    private List<Order> orders;

    /**
     * 购物车项
     */
    @TableField(exist = false)
    @JsonIgnore
    private List<CartItem> cartItems;

    /**
     * 发送的消息
     */
    @TableField(exist = false)
    @JsonIgnore
    private List<Message> sentMessages;

    /**
     * 接收的消息
     */
    @TableField(exist = false)
    @JsonIgnore
    private List<Message> receivedMessages;

    // 构造方法
    public User() {}

    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.rating = 5.0;
        this.tradeCount = 0;
        this.followerCount = 0;
        this.createTime = LocalDateTime.now();
    }

    /**
     * 判断用户是否存在（原有业务方法）
     */
    public boolean isPresent() {
        return id != null;
    }
}