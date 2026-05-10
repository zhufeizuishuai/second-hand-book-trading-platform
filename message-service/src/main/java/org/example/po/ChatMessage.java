package org.example.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_messages")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer sessionId;
    private Long senderId;
    private String senderType;   // "user" 或 "seller"
    private String contentType;  // "text", "bargain", "image", "location"
    private String content;
    private String status;       // pending, accepted, rejected

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}