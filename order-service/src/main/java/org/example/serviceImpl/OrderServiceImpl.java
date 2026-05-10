package org.example.serviceImpl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.example.api.client.BookClient;
import org.example.api.client.UserClient;
import org.example.api.config.RabbitMQConfig;
import org.example.api.dto.CartCleanupMessage;
import org.example.api.po.*;
import org.example.mapper.OrderMapper;
import org.example.service.OrderItemService;
import org.example.service.OrderService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor

public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {
    private final BookClient bookClient;
    private final UserClient userClient;
    private final RabbitTemplate rabbitTemplate;
    private final OrderItemService orderItemService;

    // 获取当前登录用户
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        // 从认证对象中获取用户名，然后从数据库中查询用户 ID
        String username = authentication.getName();
        User user = userClient.findByUsername(username);
        return (user != null && user.isPresent()) ? user.getId() : null;
    }

    @Transactional
    @Override
    public ResponseEntity<?> createOrder(Map<String, Object> orderData) {
        // 获取当前登录用户
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.badRequest().body("用户未登录");
        }

        // 从数据库获取当前用户对象
        User user = userClient.getById(currentUserId);
        if (user == null || !user.isPresent()) {
            return ResponseEntity.badRequest().body("用户不存在");
        }

        // 创建订单对象
        Order order = new Order();
        order.setBuyer(user);
        order.setBuyerId(currentUserId);

        // 生成订单号
        String orderNumber = UUID.randomUUID().toString();
        order.setOrderNumber(orderNumber);
        order.setStatus("PENDING");
        order.setCreatedAt(new Date());
        order.setUpdatedAt(new Date());

        // 处理订单项目
        List<OrderItem> orderItems = new java.util.ArrayList<>();
        double totalPrice = 0;

        if (orderData.containsKey("orderItems") && orderData.get("orderItems") instanceof List) {
            List<?> itemsList = (List<?>) orderData.get("orderItems");
            for (Object itemObj : itemsList) {
                if (itemObj instanceof Map) {
                    Map<?, ?> itemMap = (Map<?, ?>) itemObj;
                    OrderItem orderItem = new OrderItem();
                    orderItem.setOrder(order);

                    // 设置数量
                    if (itemMap.containsKey("quantity")) {
                        orderItem.setQuantity(Integer.parseInt(itemMap.get("quantity").toString()));
                    }

                    // 设置价格
                    if (itemMap.containsKey("price")) {
                        orderItem.setPrice(Double.parseDouble(itemMap.get("price").toString()));
                    }

                    // 设置书籍
                    if (itemMap.containsKey("book") && itemMap.get("book") instanceof Map) {
                        Map<?, ?> bookMap = (Map<?, ?>) itemMap.get("book");
                        if (bookMap.containsKey("id")) {
                            Long bookId = Long.parseLong(bookMap.get("id").toString());
                            Book book = bookClient.getById(bookId);
                            if (book == null) {
                                return ResponseEntity.badRequest().body("书籍信息不存在");
                            }else{
                                // 检查书籍的卖家是否是当前用户
                                if (book.getSeller() != null && book.getSeller().getId().equals(currentUserId)) {
                                    return ResponseEntity.badRequest().body("不能购买自己发布的书");
                                }
                                orderItem.setBook(book);
                                // 计算总金额
                                totalPrice += orderItem.getPrice() * orderItem.getQuantity();
                            }
                        }
                    }

                    if (orderItem.getBook() != null) {
                        orderItems.add(orderItem);
                    }
                }
            }
        }

        // 设置订单项目和总金额
        order.setOrderItems(orderItems);
        order.setTotalPrice(totalPrice);

        save(order);

        // 构建响应对象，避免循环引用
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("id", order.getId());
        response.put("orderNumber", order.getOrderNumber());
        response.put("totalPrice", order.getTotalPrice());
        response.put("status", order.getStatus());
        response.put("createdAt", order.getCreatedAt());

        // 构建订单项目列表，避免循环引用
        List<Map<String, Object>> orderItemsResponse = new java.util.ArrayList<>();
        for (OrderItem item : order.getOrderItems()) {
            Map<String, Object> itemMap = new java.util.HashMap<>();
            itemMap.put("id", item.getId());
            itemMap.put("quantity", item.getQuantity());
            itemMap.put("price", item.getPrice());
            if (item.getBook() != null) {
                Map<String, Object> bookMap = new java.util.HashMap<>();
                bookMap.put("id", item.getBook().getId());
                bookMap.put("title", item.getBook().getTitle());
                bookMap.put("author", item.getBook().getAuthor());
                bookMap.put("price", item.getBook().getPrice());
                bookMap.put("image", item.getBook().getImage());
                itemMap.put("book", bookMap);
            }
            orderItemsResponse.add(itemMap);
        }
        response.put("orderItems", orderItemsResponse);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<List<Order>> getOrders() {
        // 获取当前登录用户
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.badRequest().body(null);
        }
        List<Order> orders = query().eq("buyer_id", currentUserId).list();
        return ResponseEntity.ok(orders);
    }

    @Override
    public ResponseEntity<Order> updateOrderStatus(Long id, String status) {
        Optional<Order> orderOptional = Optional.ofNullable(getById(id));
        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();
            order.setStatus(status);
            order.setUpdatedAt(new Date());
            save(order);

            // 支付成功后发送消息，通知购物车删除对应商品
            if ("PAID".equals(status)) {
                List<OrderItem> orderItems = orderItemService.query()
                        .eq("order_id", order.getId())
                        .list();
                List<Long> bookIds = orderItems.stream()
                        .map(OrderItem::getBookId)
                        .collect(Collectors.toList());

                Long userId = order.getBuyerId();
                if (userId != null && !bookIds.isEmpty()) {
                    CartCleanupMessage message = new CartCleanupMessage(userId, bookIds);
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.ORDER_EXCHANGE,
                            RabbitMQConfig.CART_CLEANUP_ROUTING_KEY,
                            message
                    );
                }
            }

            return ResponseEntity.ok(order);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<Order> getOrderDetails(Long id) {
        Optional<Order> orderOptional = Optional.ofNullable(getById(id));
        if (orderOptional.isPresent()) {
            return ResponseEntity.ok(orderOptional.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<Order> cancelOrder(Long id) {
        Optional<Order> orderOptional = Optional.ofNullable(getById(id));
        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();
            order.setStatus("CANCELLED");
            order.setUpdatedAt(new Date());
            save(order);
            return ResponseEntity.ok(order);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
