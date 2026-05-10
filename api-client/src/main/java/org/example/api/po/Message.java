package org.example.api.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("messages")
public class Message {

    /**
     * 主键自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 消息内容
     */
    @TableField("content")
    private String content;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private Date createdAt;

    /**
     * 发送者ID（数据库真实字段）
     */
    @TableField("sender_id")
    private Long senderId;

    /**
     * 接收者ID（数据库真实字段）
     */
    @TableField("receiver_id")
    private Long receiverId;

    /**
     * 关联书籍ID
     */
    @TableField("book_id")
    private Long bookId;

    // ==================== 关联对象（非数据库字段） ====================
    /**
     * 发送者用户
     */
    @TableField(exist = false)
    private User sender;

    /**
     * 接收者用户
     */
    @TableField(exist = false)
    private User receiver;

    // 无参构造
    public Message() {}

    // 带参构造
    public Message(String content, User sender, User receiver, Long bookId) {
        this.content = content;
        this.sender = sender;
        this.receiver = receiver;
        this.bookId = bookId;
        this.createdAt = new Date();

        // 自动赋值ID（兼容原有逻辑）
        if (sender != null) {
            this.senderId = sender.getId();
        }
        if (receiver != null) {
            this.receiverId = receiver.getId();
        }
    }
}