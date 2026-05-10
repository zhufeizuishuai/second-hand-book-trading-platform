package org.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.po.ChatSession;

public interface ChatSessionService extends IService<ChatSession> {
    ChatSession getOrCreateSession(Integer bookId, Long buyerId, Long sellerId);
}