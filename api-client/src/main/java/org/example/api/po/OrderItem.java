package org.example.api.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 订单项实体类（MyBatis-Plus 版本）
 */
@Data
@TableName("order_items")
public class OrderItem {

    /**
     * 主键 ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 购买数量
     */
    @TableField(value = "quantity", exist = true)
    private int quantity;

    /**
     * 单价
     */
    @TableField(value = "price", exist = true)
    private double price;

    /**
     * 订单 ID（数据库外键字段）
     */
    @TableField("order_id")
    private Long orderId;

    /**
     * 图书 ID（数据库外键字段）
     */
    @TableField("book_id")
    private Long bookId;

    // ==================== 非数据库字段（MP 自动忽略） ====================
    /**
     * 订单对象（仅用于业务，不映射数据库）
     */
    @TableField(exist = false)
    private Order order;

    /**
     * 图书对象（仅用于业务，不映射数据库）
     */
    @TableField(exist = false)
    private Book book;
}