package org.example.api.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("cart_items")
public class CartItem {

    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 数量
     */
    @TableField("quantity")
    private Integer quantity;

    /**
     * 数据库真实外键字段
     */
    @TableField("user_id")
    private Long userId;

    @TableField("book_id")
    private Long bookId;

    // ==================== 关联对象（非数据库字段） ====================
    @TableField(exist = false)
    private User user;

    @TableField(exist = false)
    private Book book;

    // 无参构造
    public CartItem() {}

    // 带参构造（自动赋值 userId 和 bookId，兼容原有代码）
    public CartItem(int quantity, User user, Book book) {
        this.quantity = quantity;
        this.user = user;
        this.book = book;

        // 自动从对象中提取ID
        if (user != null) {
            this.userId = user.getId();
        }
        if (book != null) {
            this.bookId = book.getId();
        }
    }
}