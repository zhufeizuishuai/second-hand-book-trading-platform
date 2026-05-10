package org.example.api.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;
import java.util.List;

@Data
@TableName("orders")
public class Order {

    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 订单编号
     */
    @TableField("order_number")
    private String orderNumber;

    /**
     * 总价
     */
    @TableField("total_price")
    private double totalPrice;

    /**
     * 订单状态
     */
    @TableField("status")
    private String status;

    /**
     * 配送方式
     */
    @TableField("delivery_method")
    private String deliveryMethod;

    /**
     * 配送地址
     */
    @TableField("delivery_address")
    private String deliveryAddress;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private Date createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private Date updatedAt;

    /**
     * 买家 ID（数据库外键字段）
     */
    @TableField("buyer_id")
    private Long buyerId;

    /**
     * 买家（关联用户）
     * exist=false：不是数据库真实字段，MP不自动映射
     */
    @TableField(exist = false)
    private User buyer;

    /**
     * 订单项集合
     * exist=false：不是数据库真实字段
     */
    @TableField(exist = false)
    private List<OrderItem> orderItems;

    // 无参构造
    public Order() {}

    // 带参构造
    public Order(String orderNumber, double totalPrice, User buyer) {
        this.orderNumber = orderNumber;
        this.totalPrice = totalPrice;
        this.buyer = buyer;
        this.status = "PENDING";
        this.createdAt = new Date();
        this.updatedAt = new Date();
    }
}