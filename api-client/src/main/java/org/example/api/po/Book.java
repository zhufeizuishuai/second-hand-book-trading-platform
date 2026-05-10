package org.example.api.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import java.util.List;

@Data
@TableName("books")
public class Book {

    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 书名
     */
    @TableField("title")
    private String title;

    /**
     * 作者
     */
    @TableField("author")
    private String author;

    /**
     * 学校
     */
    @TableField("school")
    private String school;

    /**
     * ISBN
     */
    @TableField("isbn")
    private String isbn;

    /**
     * 价格
     */
    @TableField("price")
    private double price;

    /**
     * 销量
     */
    @TableField("sales")
    private int sales;

    /**
     * 距离
     */
    @TableField("distance")
    private double distance;

    /**
     * 书籍状态
     */
    @TableField("book_condition")
    private String bookCondition;

    /**
     * 图片（长文本）
     */
    @TableField("image")
    private String image;

    /**
     * 描述
     */
    @TableField("description")
    private String description;

    /**
     * 当面交易地址
     */
    @TableField("face_address")
    private String faceAddress;

    /**
     * 是否热门
     */
    @TableField("is_hot")
    private boolean isHot;

    // ==================== 数据库真实外键字段 ====================
    /**
     * 卖家ID
     */
    @TableField("seller_id")
    private Long sellerId;

    // ==================== 关联对象（非数据库字段） ====================
    /**
     * 卖家
     */
    @TableField(exist = false)
    @JsonIgnore
    private User seller;

    /**
     * 订单项
     */
    @TableField(exist = false)
    @JsonIgnore
    private List<OrderItem> orderItems;

    /**
     * 购物车项
     */
    @TableField(exist = false)
    @JsonIgnore
    private List<CartItem> cartItems;

    // 无参构造
    public Book() {}

    // 带参构造（自动赋值 sellerId，兼容原有代码）
    public Book(String title, String author, double price, User seller) {
        this.title = title;
        this.author = author;
        this.price = price;
        this.seller = seller;
        this.sales = 0;
        this.isHot = false;

        // 自动从 seller 对象中提取 ID
        if (seller != null) {
            this.sellerId = seller.getId();
        }
    }
}