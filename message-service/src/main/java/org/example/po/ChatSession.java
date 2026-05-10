package org.example.po;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_sessions")
public class ChatSession {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer bookId;
    private Long buyerId;
    private Long sellerId;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}