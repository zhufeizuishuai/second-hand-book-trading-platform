package org.example.websocket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.websocket.*;
import jakarta.websocket.server.ServerEndpoint;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.api.client.BookClient;
import org.example.api.client.UserClient;
import org.example.api.po.Book;
import org.example.api.po.User;
import org.example.api.security.JwtUtils;
import org.example.po.ChatMessage;
import org.example.po.ChatSession;
import org.example.service.ChatMessageService;
import org.example.service.ChatSessionService;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ServerEndpoint("/ws/chat")
public class ChatWebSocketServer {

    // ===================== 核心修复：静态获取 Spring 上下文 =====================
    private static ApplicationContext applicationContext;

    public static void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
    }

    // 从 Spring 上下文获取 Bean（统一工具方法）
    private static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }

    // ===================== 全部去掉 @Autowired / 构造器注入 =====================
    private StringRedisTemplate stringRedisTemplate;
    private ChatSessionService chatSessionService;
    private ChatMessageService chatMessageService;
    private UserClient userClient;
    private BookClient bookClient;
    private JwtUtils jwtUtils;

    // ===================== 必须有无参构造！Tomcat 强制要求 =====================
    public ChatWebSocketServer() {
    }

    // ===================== 通用变量 =====================
    private static final Map<Long, Session> onlineSessions = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private Long currentUserId;
    private Integer sessionId;
    private Integer bookId;
    private Long sellerId;
    private String senderType = "user";

    // ===================== 工具方法 =====================
    private Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new HashMap<>();
        if (queryString == null || queryString.isEmpty()) return params;
        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
                params.put(key, value);
            }
        }
        return params;
    }

    // ===================== 连接建立时初始化所有 Bean =====================
    @OnOpen
    public void onOpen(Session session) {
        try {
            // 【关键】在这里一次性初始化所有 Bean
            this.stringRedisTemplate = getBean(StringRedisTemplate.class);
            this.chatSessionService = getBean(ChatSessionService.class);
            this.chatMessageService = getBean(ChatMessageService.class);
            this.userClient = getBean(UserClient.class);
            this.bookClient = getBean(BookClient.class);
            this.jwtUtils = getBean(JwtUtils.class);

            // 解析参数
            String queryString = session.getQueryString();
            Map<String, String> queryParams = parseQueryString(queryString);
            String token = queryParams.get("token");
            String bookIdStr = queryParams.get("bookId");
            String sellerIdStr = queryParams.get("sellerId");

            if (token == null || bookIdStr == null) {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Missing parameters"));
                return;
            }

            Integer bookId = Integer.parseInt(bookIdStr);
            Long sellerId = (sellerIdStr != null && !sellerIdStr.isEmpty() && !"undefined".equals(sellerIdStr))
                    ? Long.parseLong(sellerIdStr) : null;

            // 验证 Token
            if (!jwtUtils.validateJwtToken(token)) {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Invalid token"));
                return;
            }

            String username = jwtUtils.getUsernameFromJwtToken(token);
            User user = userClient.findByUsername(username);
            if (user == null) {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "User not found"));
                return;
            }

            Book book = bookClient.getById(Long.valueOf(bookId));
            if (book == null) {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Book not found"));
                return;
            }

            if (sellerId == null) sellerId = book.getSellerId();
            User seller = userClient.getById(sellerId);
            if (seller == null || !sellerId.equals(book.getSellerId())) {
                session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "Seller error"));
                return;
            }

            // 获取会话
            ChatSession chatSession;
            if (user.getId().equals(sellerId)) {
                chatSession = chatSessionService.getOne(new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getBookId, bookId)
                        .eq(ChatSession::getSellerId, sellerId));
                if (chatSession == null) {
                    session.close(new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "No session"));
                    return;
                }
            } else {
                chatSession = chatSessionService.getOrCreateSession(bookId, user.getId(), sellerId);
            }

            // 赋值
            this.sessionId = chatSession.getId();
            this.currentUserId = user.getId();
            this.bookId = bookId;
            this.sellerId = sellerId;
            this.senderType = user.getId().equals(sellerId) ? "seller" : "user";

            onlineSessions.put(currentUserId, session);
            System.out.println("WebSocket 连接成功：userId=" + currentUserId);

            // 在线状态推送
            Session sellerSession = onlineSessions.get(sellerId);
            if (sellerSession != null && sellerSession.isOpen()) {
                sendMessage(sellerSession, Map.of("type", "user_online", "userId", currentUserId, "online", true));
                sendMessage(session, Map.of("type", "user_online", "userId", sellerId, "online", true));
            }

        } catch (Exception e) {
            e.printStackTrace();
            try { session.close(); } catch (IOException ex) {}
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            Map<String, Object> msgMap = objectMapper.readValue(message, Map.class);
            String type = (String) msgMap.get("type");
            if ("message".equals(type)) handleTextMessage(msgMap, session);
            else if ("bargain".equals(type)) handleBargainMessage(msgMap, session);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleTextMessage(Map<String, Object> msgMap, Session session) {
        String content = (String) msgMap.get("content");
        if (content == null || content.isEmpty()) return;

        ChatMessage chatMsg = new ChatMessage();
        chatMsg.setSessionId(sessionId);
        chatMsg.setSenderId(currentUserId);
        chatMsg.setSenderType(senderType);
        chatMsg.setContentType("text");
        chatMsg.setContent(content);
        chatMsg.setCreatedAt(LocalDateTime.now());
        chatMessageService.save(chatMsg);

        try {
            ChatSession chatSession = chatSessionService.getById(sessionId);
            if (chatSession != null) {
                String buyerId = chatSession.getBuyerId().toString();
                String sellerId = chatSession.getSellerId().toString();
                String bookId = chatSession.getBookId().toString();
                stringRedisTemplate.delete("lastMsg:" + sessionId);
                stringRedisTemplate.delete("chat:list:" + buyerId);
                stringRedisTemplate.delete("chat:list:" + sellerId);
                stringRedisTemplate.delete("chat:history:" + buyerId + ":" + bookId);
                stringRedisTemplate.delete("chat:history:" + sellerId + ":" + bookId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        broadcastMessage(chatMsg);
    }

    private void handleBargainMessage(Map<String, Object> msgMap, Session session) {
        Object priceObj = msgMap.get("price");
        if (priceObj == null) return;
        String price = priceObj.toString();

        ChatMessage chatMsg = new ChatMessage();
        chatMsg.setSessionId(sessionId);
        chatMsg.setSenderId(currentUserId);
        chatMsg.setSenderType(senderType);
        chatMsg.setContentType("bargain");
        chatMsg.setContent(price);
        chatMsg.setStatus("pending");
        chatMsg.setCreatedAt(LocalDateTime.now());
        chatMessageService.save(chatMsg);
        broadcastMessage(chatMsg);
    }

    private void broadcastMessage(ChatMessage chatMsg) {
        Map<String, Object> broadcast = new HashMap<>();
        broadcast.put("type", "message");
        broadcast.put("messageId", chatMsg.getId());
        broadcast.put("senderType", chatMsg.getSenderType());
        broadcast.put("senderId", chatMsg.getSenderId());
        broadcast.put("contentType", chatMsg.getContentType());
        broadcast.put("content", chatMsg.getContent());
        broadcast.put("timestamp", chatMsg.getCreatedAt().toString());
        broadcast.put("status", chatMsg.getStatus());

//        Session selfSession = onlineSessions.get(currentUserId);
//        if (selfSession != null && selfSession.isOpen()) sendMessage(selfSession, broadcast);

        Long targetId = sellerId;
        if ("seller".equals(senderType)) {
            ChatSession chatSession = chatSessionService.getById(sessionId);
            stringRedisTemplate.delete("lastMsg:" + chatMsg.getSessionId());
            if (chatSession != null) targetId = chatSession.getBuyerId();
        }

        Session targetSession = onlineSessions.get(targetId);
        if (targetSession != null && targetSession.isOpen()) {
            Map<String, Object> forTarget = new HashMap<>(broadcast);
            forTarget.put("senderType", "user".equals(senderType) ? "seller" : "user");
            sendMessage(targetSession, forTarget);
        }
    }

    private void sendMessage(Session session, Map<String, Object> data) {
        try {
            session.getBasicRemote().sendText(objectMapper.writeValueAsString(data));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnClose
    public void onClose() {
        if (currentUserId != null) {
            onlineSessions.remove(currentUserId);
            Session sellerSession = onlineSessions.get(sellerId);
            if (sellerSession != null && sellerSession.isOpen()) {
                sendMessage(sellerSession, Map.of("type", "user_online", "userId", currentUserId, "online", false));
            }
            System.out.println("WebSocket 断开：userId=" + currentUserId);
        }
    }

    @OnError
    public void onError(Throwable error) {
        error.printStackTrace();
    }
}