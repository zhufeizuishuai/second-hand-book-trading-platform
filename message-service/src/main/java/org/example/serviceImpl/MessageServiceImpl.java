package org.example.serviceImpl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.example.api.client.BookClient;
import org.example.api.client.UserClient;
import org.example.api.dto.UserDTO;
import org.example.api.po.Book;
import org.example.api.po.User;
import org.example.mapper.MessageMapper;
import org.example.po.ChatMessage;
import org.example.po.ChatSession;
import org.example.po.Message;
import org.example.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private final UserClient userService;
    private final BookClient bookService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageService chatMessageService;
    private final StringRedisTemplate stringRedisTemplate;

    private UserDTO getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        String username = authentication.getName();
        String userJson = stringRedisTemplate.opsForValue().get("currentUser:" + username);
        if (userJson != null && !userJson.isEmpty()) {
            return JSONUtil.toBean(userJson, UserDTO.class);
        }
        User user = userService.findByUsername(username);
        if (user == null) {
            return null;
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        if (userDTO != null) {
            stringRedisTemplate.opsForValue().set("currentUser:" + username, JSONUtil.toJsonStr(userDTO));
        }
        return userDTO;
    }

    @Override
    public ResponseEntity<?> getHistory(String bookId) {
        UserDTO currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.ok(emptyHistoryResponse());
        }

        // ===================== 1. 书籍缓存（你原有逻辑保留） =====================
        String bookJson = stringRedisTemplate.opsForValue().get("book:" + bookId);
        Book book = null;
        if(bookJson != null){
            book = JSONUtil.toBean(bookJson, Book.class);
        }
        else {
            book = bookService.getById(Long.valueOf(bookId));
            if (book != null) {
                stringRedisTemplate.opsForValue().set("book:" + bookId, JSONUtil.toJsonStr(book));
            }
        }
        if (book == null) return ResponseEntity.ok(emptyHistoryResponse());

        // ===================== 2. 卖家信息缓存（你原有逻辑保留） =====================
        String sellerJson = stringRedisTemplate.opsForValue().get("user:" + book.getSellerId());
        User seller = null;
        if(sellerJson != null){
            seller = JSONUtil.toBean(sellerJson, User.class);
        }else {
            seller=userService.getById(book.getSellerId());
            if (seller != null) {
                stringRedisTemplate.opsForValue().set("user:" + book.getSellerId(), JSONUtil.toJsonStr(seller));
            }
        }
        if (seller == null) return ResponseEntity.ok(emptyHistoryResponse());

        // ===================== 3. 拼接聊天记录缓存 KEY（核心新增） =====================
        // 规则：chat:history:用户ID:书籍ID （确保每个用户看自己的聊天缓存独立）
        String cacheKey = "chat:history:" + currentUser.getId() + ":" + bookId;

        // ===================== 4. 先从Redis取聊天记录缓存 =====================
        String historyCache = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(historyCache)) {
            // 缓存存在 → 直接返回（不走数据库）
            Map<String, Object> cacheData = JSONUtil.toBean(historyCache, Map.class);
            return ResponseEntity.ok(cacheData);
        }

        // ===================== 5. 缓存不存在 → 查询数据库 =====================
        ChatSession session = chatSessionService.getOne(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getBookId, bookId)
                        .and(wrapper -> wrapper
                                .eq(ChatSession::getBuyerId, currentUser.getId())
                                .or()
                                .eq(ChatSession::getSellerId, currentUser.getId())
                        )
        );

        List<Map<String, Object>> formattedMessages = new ArrayList<>();
        if (session != null) {
            List<ChatMessage> messages = chatMessageService.list(
                    new LambdaQueryWrapper<ChatMessage>()
                            .eq(ChatMessage::getSessionId, session.getId())
                            .orderByAsc(ChatMessage::getCreatedAt)
            );
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
            for (ChatMessage msg : messages) {
                Map<String, Object> fm = new HashMap<>();
                fm.put("type", msg.getSenderType());
                fm.put("contentType", msg.getContentType());
                fm.put("content", msg.getContent());
                fm.put("status", msg.getStatus());
                fm.put("senderId", msg.getSenderId());
                String timeStr = "";
                if (msg.getCreatedAt() != null) {
                    timeStr = msg.getCreatedAt().format(timeFormatter);
                } else {
                    timeStr = "未知时间";
                }
                fm.put("time", timeStr);
                formattedMessages.add(fm);
            }
        }

        // 组装返回数据
        Map<String, Object> sellerMap = new HashMap<>();
        sellerMap.put("name", seller.getUsername());
        sellerMap.put("rating", seller.getRating());
        sellerMap.put("id", seller.getId());

        Map<String, Object> bookMap = new HashMap<>();
        bookMap.put("title", book.getTitle());
        bookMap.put("price", book.getPrice());
        bookMap.put("image", book.getImage() != null ? book.getImage() : "");
        bookMap.put("sellerId", book.getSellerId());

        Map<String, Object> data = new HashMap<>();
        data.put("seller", sellerMap);
        data.put("book", bookMap);
        data.put("messages", formattedMessages);

        // ===================== 6. 存入Redis缓存（新增） =====================
        // 缓存 30 分钟，避免数据长期不更新
        stringRedisTemplate.opsForValue().set(
                cacheKey,
                JSONUtil.toJsonStr(data),
                30, TimeUnit.MINUTES
        );

        return ResponseEntity.ok(data);
    }

    private Map<String, Object> emptyHistoryResponse() {
        return Map.of("seller", Map.of(), "book", Map.of(), "messages", new ArrayList<>());
    }

    @Override
    public ResponseEntity<?> getChatList() {
        UserDTO currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.ok(new ArrayList<>());

        // 🔥 缓存 KEY：每个用户的聊天列表独立缓存
        String cacheKey = "chat:list:" + currentUser.getId();

        // 1. 先查 Redis 缓存
        String cacheJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cacheJson != null && !cacheJson.isEmpty()) {
            // 把 Hutool 的 JSON 字符串转成 Java 原生 List
            List<Map> cacheList = JSONUtil.toList(cacheJson, Map.class);
            return ResponseEntity.ok(cacheList);
        }

        // 2. 缓存不存在 → 查询数据库
        List<ChatSession> sessions = chatSessionService.list(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getBuyerId, currentUser.getId())
                        .or()
                        .eq(ChatSession::getSellerId, currentUser.getId())
        );

        List<Map<String, Object>> chatList = new ArrayList<>();
        DateTimeFormatter dtf = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        for (ChatSession session : sessions) {
            // ========== 书籍信息缓存 ==========
            String bookCacheKey = "book:" + session.getBookId();
            String bookJson = stringRedisTemplate.opsForValue().get(bookCacheKey);
            Book book = null;

            if (bookJson != null) {
                book = JSONUtil.toBean(bookJson, Book.class);
            } else {
                book = bookService.getById(Long.valueOf(session.getBookId()));
                if (book != null) {
                    stringRedisTemplate.opsForValue().set(bookCacheKey, JSONUtil.toJsonStr(book), 60, TimeUnit.MINUTES);
                }
            }
            if (book == null) continue;

            // ========== 卖家信息缓存 ==========
            String userCacheKey = "user:" + session.getSellerId();
            String userDTOJson = stringRedisTemplate.opsForValue().get(userCacheKey);
            User seller = null;

            if (userDTOJson != null) {
                UserDTO userDTO = JSONUtil.toBean(userDTOJson, UserDTO.class);
                seller = new User();
                BeanUtil.copyProperties(userDTO, seller);
            } else {
                seller = userService.getById(session.getSellerId());
                if (seller != null) {
                    UserDTO userDTO = new UserDTO();
                    BeanUtil.copyProperties(seller, userDTO);
                    stringRedisTemplate.opsForValue().set(userCacheKey, JSONUtil.toJsonStr(userDTO), 60, TimeUnit.MINUTES);
                }
            }
            if (seller == null) continue;

            // ========== 最后一条消息缓存 ==========
            String lastMsgCacheKey = "lastMsg:" + session.getId();
            String lastMsgJson = stringRedisTemplate.opsForValue().get(lastMsgCacheKey);
            ChatMessage lastMsg = null;

            if (lastMsgJson != null && !lastMsgJson.isEmpty()) {
                lastMsg = JSONUtil.toBean(lastMsgJson, ChatMessage.class);
            } else {
                lastMsg = chatMessageService.getOne(
                        new LambdaQueryWrapper<ChatMessage>()
                                .eq(ChatMessage::getSessionId, session.getId())
                                .orderByDesc(ChatMessage::getCreatedAt)
                                .last("LIMIT 1")
                );
                if (lastMsg != null) {
                    stringRedisTemplate.opsForValue().set(lastMsgCacheKey, JSONUtil.toJsonStr(lastMsg), 5, TimeUnit.MINUTES);
                }
            }

            // ========== 组装返回数据 ==========
            Map<String, Object> chat = new HashMap<>();
            chat.put("id", "chat_" + session.getBookId());

            Map<String, Object> sellerMap = new HashMap<>();
            sellerMap.put("id", seller.getId().toString());
            sellerMap.put("name", seller.getUsername());
            sellerMap.put("avatar", seller.getAvatar() != null ? seller.getAvatar() : "");
            chat.put("seller", sellerMap);

            Map<String, Object> bookMap = new HashMap<>();
            bookMap.put("id", book.getId().toString());
            bookMap.put("title", book.getTitle());
            bookMap.put("price", book.getPrice());
            bookMap.put("image", book.getImage() != null ? book.getImage() : "");
            chat.put("book", bookMap);

            if (lastMsg != null) {
                chat.put("lastMessage", lastMsg.getContent());
                chat.put("lastMessageTime", lastMsg.getCreatedAt().format(dtf));
            } else {
                chat.put("lastMessage", "");
                chat.put("lastMessageTime", session.getCreatedAt().format(dtf));
            }
            chat.put("unreadCount", 0);

            chatList.add(chat);
        }

        // 🔥 3. 把最终聊天列表存入 Redis，缓存 5 分钟
        stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(chatList), 5, TimeUnit.MINUTES);

        return ResponseEntity.ok(chatList);
    }

    @Override
    public ResponseEntity<?> handleBargain(Map<String, Object> request) {
        // 处理卖家接受/拒绝砍价
        UserDTO currentUser = getCurrentUser();
        if (currentUser == null) return ResponseEntity.ok(Map.of("code", 401, "message", "未登录"));

        String messageIdStr = (String) request.get("bargainId");
        String status = (String) request.get("status"); // "accepted" or "rejected"
        if (messageIdStr == null || status == null) {
            return ResponseEntity.ok(Map.of("code", 400, "message", "参数错误"));
        }

        Integer messageId = Integer.parseInt(messageIdStr);
        ChatMessage message = chatMessageService.getById(messageId);
        if (message == null || !"bargain".equals(message.getContentType())) {
            return ResponseEntity.ok(Map.of("code", 400, "message", "砍价消息不存在"));
        }

        ChatSession session = chatSessionService.getById(message.getSessionId());
        if (session == null || !session.getSellerId().equals(currentUser.getId())) {
            return ResponseEntity.ok(Map.of("code", 403, "message", "无权处理"));
        }

        message.setStatus(status);
        chatMessageService.updateById(message);

        // 通过 WebSocket 通知买家（如需要，可注入 ChatWebSocketServer 的静态方法发送）
        // 这里可调用一个工具方法发送更新消息

        return ResponseEntity.ok(Map.of("code", 200, "message", "处理成功"));
    }
}