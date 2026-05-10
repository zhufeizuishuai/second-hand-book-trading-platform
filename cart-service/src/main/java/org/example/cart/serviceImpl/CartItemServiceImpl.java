package org.example.cart.serviceImpl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import org.example.api.client.BookClient;
import org.example.api.client.UserClient;
import org.example.api.dto.UserDTO;
import org.example.api.po.Book;
import org.example.api.po.CartItem;
import org.example.api.po.User;
import org.example.cart.mapper.CartItemMapper;
import org.example.cart.service.CartItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartItemServiceImpl extends ServiceImpl<CartItemMapper, CartItem> implements CartItemService {

    private final UserClient userService;
    private final CartItemMapper cartItemMapper;

    private final BookClient bookClient;

//    private final CartItemService cartItemService;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public ResponseEntity<?> getCartItems() {
        Long currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.badRequest().body("用户未登录");
        }
        String cartHashKey = "cart:user:" + currentUserId;

        // 1. 先查 Redis Hash
        Map<Object, Object> cartHash = stringRedisTemplate.opsForHash().entries(cartHashKey);
        if (!cartHash.isEmpty()) {
            List<Map<String, Object>> list = cartHash.values().stream()
                    .map(json -> {
                        // 一行搞定！
                        return (Map<String, Object>)JSONUtil.toBean(json.toString(), Map.class);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(list);
        }

        // 2. 缓存无数据 → 查库
        QueryWrapper<CartItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", currentUserId);
        List<CartItem> cartItems = cartItemMapper.selectList(queryWrapper);

        List<Map<String, Object>> responseItems = cartItems.stream().map(item -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", item.getId());
            map.put("quantity", item.getQuantity());
            Book book = bookClient.getById(item.getBookId());
            if (book != null) {
                map.put("bookId", book.getId());
                map.put("title", book.getTitle());
                map.put("author", book.getAuthor());
                map.put("price", book.getPrice());
                map.put("image", book.getImage());
                map.put("condition", book.getBookCondition());
            }
            return map;
        }).collect(Collectors.toList());

        // 3. 批量写入 Redis Hash
        for (Map<String, Object> item : responseItems) {
            String itemId = String.valueOf(item.get("id"));
            String json;
            try {
                 json = JSONUtil.toJsonStr(item);
                // Hash: field=购物车id , value=单条json
                stringRedisTemplate.opsForHash().put(cartHashKey, itemId, json);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 可加过期时间
        stringRedisTemplate.expire(cartHashKey, 30, TimeUnit.MINUTES);

        return ResponseEntity.ok(responseItems);
    }

    //加入购物车
    @Override
    public ResponseEntity<?> addToCart(CartItem cartItem) {
        // 获取当前登录用户
        Long currentUserId = getCurrentUserId();
        String cartHashKey = "cart:user:" + currentUserId;

        if (currentUserId == null) {
            return ResponseEntity.badRequest().body("用户未登录");
        }

        // 检查书籍是否存在
        if (cartItem.getBook() == null || cartItem.getBook().getId() == null) {
            return ResponseEntity.badRequest().body("书籍信息不存在");
        }

        // 检查书籍
        Book book = bookClient.getById(cartItem.getBook().getId());
        if (book == null) {
            return ResponseEntity.badRequest().body("书籍不存在");
        }

        // 不能加自己的书
        if (book.getSellerId() != null && book.getSellerId().equals(currentUserId)) {
            return ResponseEntity.badRequest().body("不能将自己发布的书加入购物车");
        }

        // 检查商品是否已在购物车中
        Optional<CartItem> existingItem = findByUserIdAndBookId(currentUserId, book.getId());
        CartItem savedItem;

        if (existingItem.isPresent()) {
            // ==============================================
            // 已存在 → 数量相加
            // ==============================================
            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + cartItem.getQuantity());
            save(item);
            savedItem = item;

            // ----------------------
            // Redis 局部更新数量
            // ----------------------
            try {
                // 组装最新数据
                Map<String, Object> cartMap = buildCartMap(savedItem);
                String json = objectMapper.writeValueAsString(cartMap);
                // 只更新这一条！
                stringRedisTemplate.opsForHash().put(cartHashKey, savedItem.getId().toString(), json);
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {
            // ==============================================
            // 不存在 → 新建购物车项
            // ==============================================
            User currentUser = userService.getById(currentUserId);
            cartItem.setUser(currentUser);
            cartItem.setBook(book);
            cartItem.setBookId(book.getId());
            cartItem.setUserId(currentUserId);
            save(cartItem);
            savedItem = cartItem;

            // ----------------------
            // Redis 新增一条
            // ----------------------
            try {
                Map<String, Object> cartMap = buildCartMap(savedItem);
                String json = objectMapper.writeValueAsString(cartMap);
                stringRedisTemplate.opsForHash().put(cartHashKey, savedItem.getId().toString(), json);
                // 设置30分钟过期
                stringRedisTemplate.expire(cartHashKey, 30, TimeUnit.MINUTES);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return ResponseEntity.ok("加入购物车成功");
    }

    // ==============================================
// 工具方法：把 CartItem 转成前端需要的 Map结构
// ==============================================
    private Map<String, Object> buildCartMap(CartItem item) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", item.getId());
        map.put("quantity", item.getQuantity());

        Book book = item.getBook();
        if (book != null) {
            map.put("bookId", book.getId());
            map.put("title", book.getTitle());
            map.put("author", book.getAuthor());
            map.put("price", book.getPrice());
            map.put("image", book.getImage());
            map.put("condition", book.getBookCondition());
        }
        return map;
    }

    @Override
    public Optional<CartItem> findByUserIdAndBookId(Long currentUserId, Long id) {
        QueryWrapper<CartItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", currentUserId).eq("book_id", id);
        return Optional.ofNullable(cartItemMapper.selectOne(queryWrapper));
    }

    @Override
    public ResponseEntity<?> clearCart() {
        // 获取当前登录用户
        Long currentUserId = getCurrentUserId();
        String cartHashKey = "cart:user:" + currentUserId;
        if (currentUserId == null) {
            return ResponseEntity.badRequest().body("用户未登录");
        }

        List<CartItem> cartItems = query().eq("user_id", currentUserId).list();
        cartItems.forEach(item -> {
            item.setUser(null);
            item.setBook(null);
        });
        List<Long>cartItemsIds = cartItems.stream().map(CartItem::getId).collect(Collectors.toList());
        removeByIds(cartItemsIds);
        stringRedisTemplate.delete(cartHashKey);
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Map<String, Object>> updateQuantity(Long id, Integer quantity) {
        if (quantity == null) {
            return ResponseEntity.badRequest().build();
        }

        CartItem cartItem = getById(id);
        if (cartItem != null) {
            // 计算新数量
            int newQuantity = cartItem.getQuantity() + quantity;

            // 数量 <=0 则删除商品
            if (newQuantity <= 0) {
                removeById(cartItem.getId());

                // ==============================
                // 🔥 Redis 删除单条
                // ==============================
                String cartHashKey = "cart:user:" + cartItem.getUserId();
                stringRedisTemplate.opsForHash().delete(cartHashKey, id.toString());

                return ResponseEntity.noContent().build();
            }

            // 更新数量
            cartItem.setQuantity(newQuantity);
            updateById(cartItem);

            // 构建返回数据
            Map<String, Object> response = new HashMap<>();
            Book book = bookClient.getById(cartItem.getBookId());
            response.put("id", cartItem.getId());
            response.put("quantity", cartItem.getQuantity());

            if (book != null) {
                response.put("bookId", book.getId());
                response.put("title", book.getTitle());
                response.put("author", book.getAuthor());
                response.put("price", book.getPrice());
                response.put("image", book.getImage());
                response.put("condition", book.getBookCondition());
            }

            // ==============================
            // 🔥 重点：只更新 Redis 单条数据
            // ==============================
            try {
                String cartHashKey = "cart:user:" + cartItem.getUserId();
                String json = objectMapper.writeValueAsString(response);

                // 只更新这一条！不影响其他数据
                stringRedisTemplate.opsForHash().put(cartHashKey, id.toString(), json);
            } catch (Exception e) {
                e.printStackTrace();
            }

            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Override
    public ResponseEntity<?> deleteById(Long id) {
        boolean delete = removeById(id);
        if(delete == true){
        Long currentUserId = getCurrentUserId();
        String cartHashKey = "cart:user:" + currentUserId;
        stringRedisTemplate.opsForHash().delete(cartHashKey, id.toString());
            return ResponseEntity.ok(delete);
        }
        return ResponseEntity.ok(delete);
    }

    @Override
    public void deleteByUserIdAndBookIds(Long userId, List<Long> bookIds) {
        if (userId == null || bookIds == null || bookIds.isEmpty()) {
            return;
        }
        String cartHashKey = "cart:user:" + userId;

        QueryWrapper<CartItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).in("book_id", bookIds);
        List<CartItem> itemsToRemove = cartItemMapper.selectList(queryWrapper);

        for (CartItem item : itemsToRemove) {
            removeById(item.getId());
            stringRedisTemplate.opsForHash().delete(cartHashKey, item.getId().toString());
        }
    }

    // 获取当前登录用户
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        String username = authentication.getName();
        String userJson = stringRedisTemplate.opsForValue().get("currentUser:" + username);
        if (userJson != null && !userJson.isEmpty()) {
            return JSONUtil.toBean(userJson, UserDTO.class).getId();
        }
        User user = userService.findByUsername(username);
        if (user == null) {
            return null;
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        if (userDTO != null) {
            stringRedisTemplate.opsForValue().set("currentUser:" + username, JSONUtil.toJsonStr(userDTO));
        }
        return userDTO != null ? userDTO.getId() : null;
    }
}
