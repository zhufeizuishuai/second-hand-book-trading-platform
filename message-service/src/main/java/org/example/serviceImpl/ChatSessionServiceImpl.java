package org.example.serviceImpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.mapper.ChatSessionMapper;
import org.example.po.ChatSession;
import org.example.service.ChatSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ChatSessionServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatSessionService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public ChatSession getOrCreateSession(Integer bookId, Long buyerId, Long sellerId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getBookId, bookId)
               .eq(ChatSession::getBuyerId, buyerId)
               .eq(ChatSession::getSellerId, sellerId);
        ChatSession session = getOne(wrapper);

        if (session == null) {
            session = new ChatSession();
            session.setBookId(bookId);
            session.setBuyerId(buyerId);
            session.setSellerId(sellerId);
            session.setCreatedAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            save(session);
        }
        session.setUpdatedAt(LocalDateTime.now());

        // 1. 清理双方的聊天列表缓存
        stringRedisTemplate.delete("chat:list:" + buyerId);
        stringRedisTemplate.delete("chat:list:" + sellerId);

        // 2. 清理双方的聊天记录缓存
        stringRedisTemplate.delete("chat:history:" + buyerId + ":" + bookId);
        stringRedisTemplate.delete("chat:history:" + sellerId + ":" + bookId);

        stringRedisTemplate.delete("lastMsg:" + session.getId());

        return session;
    }
}