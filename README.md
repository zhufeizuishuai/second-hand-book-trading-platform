# 二手书交易平台

基于 Spring Cloud 微服务架构的校园二手书在线交易平台，支持用户认证、图书发布与智能识别、购物车、订单管理、实时通讯等完整电商交易链路。

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.2.4 |
| 微服务 | Spring Cloud (Gateway / LoadBalancer) | 2023.0.2 |
| 微服务 | Spring Cloud Alibaba (Nacos / Sentinel) | 2023.0.1.0 |
| ORM | MyBatis-Plus | 3.5.5 |
| 数据库 | MySQL | 8.3.0 |
| 缓存 | Redis (Lettuce) | — |
| 消息队列 | RabbitMQ (Spring AMQP) | 3.2.4 |
| 认证鉴权 | Spring Security + JJWT (HMAC-SHA512) | 0.11.5 |
| 实时通讯 | WebSocket (Jakarta WebSocket) | — |
| 服务调用 | OpenFeign + Sentinel | — |
| 对象存储 | Alibaba Cloud OSS | 3.17.4 |
| AI 识别 | Baidu AI OCR | 4.15.3 |
| 工具库 | Hutool | 5.7.17 |
| 简化代码 | Lombok | 1.18.36 |

## 项目架构

```
second-hand-book-trading-platform/
├── api-client/              # 共享模块：Feign 客户端、DTO、PO、安全组件、通用配置
├── gateway-service/         # API 网关：统一入口、路由转发、Sentinel 限流
├── auth-service/            # 认证服务：登录、注册、JWT 令牌签发
├── user-service/            # 用户服务：个人信息管理、头像上传
├── book-service/            # 图书服务：图书 CRUD、搜索、OSS 上传、AI 识别
├── cart-service/            # 购物车服务：购物车管理、Redis 缓存、RabbitMQ 消费
├── order-service/           # 订单服务：订单创建、状态流转、消息发送
├── message-service/         # 消息服务：聊天记录、会话列表、WebSocket 实时推送
└── pom.xml                  # 父 POM，统一依赖版本管理
```

### 服务调用拓扑

```
                     ┌─────────────────┐
                     │  Nacos 注册中心   │
                     └────────┬────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
   ┌────▼────┐          ┌────▼────┐          ┌────▼────┐
   │ Gateway │          │ Sentinel│          │ 其他服务  │
   │ :8080   │          │ Dashboard│        │ :8081~  │
   └────┬────┘          └─────────┘          └─────────┘
        │
   lb://路由转发
        │
   ┌────┼──────────────┬──────────────┬──────────────┐
   │    │              │              │              │
┌──▼──┐ ┌▼────┐  ┌────▼─┐  ┌──────▼─┐  ┌──────▼───┐
│Auth │ │Book │  │User  │  │Order   │  │Cart/Message
│8081 │ │8082 │  │8083  │  │8084    │  │ ...
└─────┘ └─────┘  └──────┘  └────────┘  └──────────┘

   OpenFeign 服务间调用   RabbitMQ 异步解耦
```

## 核心功能模块

### 1. 用户认证（auth-service + api-client）

- **JWT 无状态认证**：基于 JJWT (HMAC-SHA512) 签发与校验，Token 24h 过期
- **认证过滤器**：`JwtAuthFilter` 拦截 HTTP 请求，从 `Authorization: Bearer <token>` 提取并校验
- **分级鉴权**：
  - `/api/auth/**`、`/api/books/**` 等公共接口放行
  - `/api/user/**` 等需认证接口拦截
  - WebSocket `/ws/**` 独立鉴权（Token 通过 Query Param 传递，不经过 HTTP Filter Chain）
- **密码编码**：BCrypt 加密存储
- **登录后用户信息写入 Redis**，后续服务间调用优先从缓存读取

### 2. 图书服务（book-service）

- **多维度搜索**：关键字模糊匹配 + 价格/销量/距离多维度排序 + 学校校区筛选
- **Redis 缓存策略**：图书详情优先查 Redis（`book:{id}`），未命中则查库并回写缓存
- **图片上传**：前端直传 Alibaba Cloud OSS，后端生成唯一文件名（`books/{UUID}.{ext}`）并返回 URL
- **AI 智能识别**：集成百度 OCR SDK，用户上传书封图片后自动提取书名、作者、ISBN，生成描述信息
- **Sentinel 流控**：对 `getBookDetails`、`uploadBook` 接口实施限流，超出阈值返回 429

### 3. 购物车（cart-service）

- **Redis Hash 存储**：`cart:user:{userId}` 为 Hash Key，field = 购物车项 ID，value = JSON 序列化数据
- **30 分钟惰性过期**：每次操作刷新 TTL，兼顾命中率与内存占用
- **缓存一致性**：增删改查操作同时更新 MySQL 和 Redis Hash 单条 Field，避免全量刷新
- **防呆设计**：禁止将自己发布的书籍加入购物车
- **RabbitMQ 异步清理**：订单支付成功后，Order 服务发送 `CartCleanupMessage` 到 `order.exchange` → `cart.cleanup.queue`，购物车服务消费后自动移除已购商品

### 4. 订单服务（order-service）

- **事务订单创建**：`@Transactional` 保证订单 + 订单项原子写入
- **订单状态流转**：PENDING → PAID / CANCELLED
- **支付后异步解耦**：状态变更为 PAID 时，通过 RabbitMQ 发送购物车清理消息（削峰填谷，降低接口 RT）
- **防呆设计**：下单时校验不能购买自己发布的书籍

### 5. 实时通讯（message-service）

- **WebSocket 会话管理**：`ChatWebSocketServer` 基于 `@ServerEndpoint("/ws/chat")` 实现，通过 `ConcurrentHashMap<Long, Session>` 维护在线用户
- **握手认证**：Token 通过 URL Query String 传递，在 `onOpen` 中校验 JWT、解析用户身份、验证书籍与卖家关系
- **4 种消息类型**：
  - `text` — 普通文本聊天
  - `bargain` — 议价（含 pending/accepted/rejected 状态）
  - `image` — 图片消息
  - `location` — 位置分享
- **多级缓存加速**：
  - 聊天记录：`chat:history:{userId}:{bookId}` — 30 分钟缓存
  - 聊天列表：`chat:list:{userId}` — 5 分钟缓存
  - 最后消息：`lastMsg:{sessionId}` — 5 分钟缓存
  - 新消息写入后主动删除相关缓存，保证数据新鲜度
- **在线状态广播**：用户上线/下线时向对方推送 `user_online` 事件
- **Spring Bean 注入解决**：通过 `ApplicationContext` 静态持有 + `@PostConstruct` 初始化，解决 WebSocket 多实例端点无法直接注入 Bean 的问题

### 6. API 网关（gateway-service）

- **统一路由**：基于 Spring Cloud Gateway，所有 `/api/**` 请求通过 `lb://` 负载均衡转发到对应微服务
- **WebSocket 路由**：`lb:ws://message-service` 代理 WebSocket 连接
- **Sentinel 网关流控**：`SentinelGatewayConfig` 注册全局 BlockHandler，限流时返回 JSON 格式 `{"code": 429, "message": "..."}`
- **规则持久化**：Sentinel 规则推送至 Nacos，支持 `gw-flow`（网关流控）和 `degrade`（降级）规则动态生效
- **全局 CORS**：网关层统一处理跨域，避免各服务重复配置

### 7. 共享模块（api-client）

- **Feign 客户端**：`UserClient`、`BookClient`、`OrderClient`、`CartClient`、`MessageClient`，统一服务间契约
- **Fallback 工厂**：`UserClientFallbackFactory`、`BookClientFallbackFactory`、`CartClientFallbackFactory`，集成 Sentinel 降级逻辑
- **安全组件**：`JwtAuthFilter`、`JwtUtils`、`UserDetailsServiceImpl`、`SecurityConfig`，所有服务复用
- **通用 PO/DTO**：`User`、`Book`、`Order`、`CartItem`、`Message` 等实体和传输对象
- **RabbitMQ 配置**：`RabbitMQConfig` 统一声明交换机、队列、绑定关系
- **Jackson 配置**：统一 JSON 序列化规则

## 数据流举例

### 用户下单 → 购物车清理流程

```
用户下单        Order Service          RabbitMQ         Cart Service        用户购物车
   │                │                     │                 │                   │
   ├─ POST /orders ─►│                     │                 │                   │
   │                ├─ 创建订单 + 订单项    │                 │                   │
   │                ├─ 状态→PAID          │                 │                   │
   │                ├─ 发送 CartCleanup ──►│                 │                   │
   │                │                  order.exchange      │                   │
   │                │                     ├─ 路由到 ──────►│                    │
   │                │                     │ cart.cleanup    ├─ 解析 bookIds     │
   │                │                     │  .queue          ├─ deleteByUserId   │
   │                │                     │                 │  AndBookIds       │
   │                │                     │                 ├─ MySQL DELETE ────┤
   │                │                     │                 ├─ Redis HDEL ──────┤ 购物车更新
```

### WebSocket 实时通讯流程

```
买家 Browser                      message-service                    卖家 Browser
     │                                  │                                  │
     ├─ WS /ws/chat?token=&bookId= ──► │                                  │
     │                               ├─ 校验 JWT                          │
     │                               ├─ 查询/创建 ChatSession               │
     │                               ├─ onlineSessions.put(buyerId)        │
     │                               ├─ 推送 user_online ─────────────────►│
     │                               │                                  │
     │  ──── 发送文本消息 ──────────► │                                  │
     │                               ├─ 持久化 ChatMessage                 │
     │                               ├─ 删除缓存 (lastMsg, chat:list,       │
     │                               │    chat:history)                    │
     │                               ├─ broadcast ──────────────────────►│
     │                               │                                  ├─ 渲染消息
     │                               │                                  │
     │  ──── 发送议价 price=25 ─────► │                                  │
     │                               ├─ 状态=pending, broadcast ────────►│
     │                               │  ◄─── POST /bargain/handle ──────┤
     │                               │  status=accepted/rejected           │
     │  ◄─── 通知结果 ──────────────  │                                  │
```

## 环境依赖

| 中间件 | 用途 | 默认地址 |
|--------|------|----------|
| MySQL | 持久化数据库 | `localhost:3306/second-hand-book-trading-platform` |
| Redis | 缓存 & 会话 | `192.168.100.128:6379` |
| RabbitMQ | 异步消息 | `192.168.100.128:5672` |
| Nacos | 注册发现 & 配置中心 | `192.168.100.128:8848` |
| Sentinel Dashboard | 流量监控 & 规则管理 | `192.168.100.128:8858` |
| Alibaba Cloud OSS | 图片存储 | `oss-cn-beijing.aliyuncs.com` |
| Baidu AI OCR | 图书封面识别 | 需申请 API Key |

## 快速开始

### 1. 准备环境

确保已安装 JDK 21、Maven 3.x，并启动 MySQL、Redis、RabbitMQ、Nacos。

### 2. 初始化数据库

```sql
CREATE DATABASE IF NOT EXISTS `second-hand-book-trading-platform` DEFAULT CHARACTER SET utf8mb4;
```

项目启动后 MyBatis-Plus 会自动建表（或手动执行 DDL）。

### 3. 修改配置

编辑各模块 `src/main/resources/application.yml`，将数据库密码、Redis 密码、RabbitMQ 凭证、OSS Key、OCR Key 替换为自己的配置。

### 4. 启动服务

按推荐顺序启动各模块：

```bash
# 1. 基础设施（确保 MySQL、Redis、RabbitMQ、Nacos 已运行）

# 2. 启动网关
cd gateway-service && mvn spring-boot:run

# 3. 启动各业务服务（可并行）
cd auth-service && mvn spring-boot:run
cd user-service && mvn spring-boot:run
cd book-service && mvn spring-boot:run
cd order-service && mvn spring-boot:run
cd cart-service && mvn spring-boot:run
cd message-service && mvn spring-boot:run
```

### 5. 验证

```bash
# 注册用户
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456","email":"test@example.com","campus":"信息学部"}'

# 登录获取 Token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 搜索图书
curl "http://localhost:8080/api/books/search?q=Java&sort=price_asc&school=信息学部"
```

## API 接口概览

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录，返回 JWT + UserDTO |
| POST | `/api/auth/register` | 注册新用户 |

### 图书

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/books/recommended` | 推荐图书（按销量降序，可选 school 过滤） |
| GET | `/api/books/search?q=&sort=&school=` | 关键字搜索，多维度排序 |
| GET | `/api/books/{id}` | 图书详情（Redis 缓存优先） |
| POST | `/api/books` | 发布图书 |
| GET | `/api/books/user/{sellerId}` | 某卖家发布的图书 |
| POST | `/api/books/upload` | 上传图片到 OSS |
| POST | `/api/books/ai-recognize` | AI 识别图书封面 |

### 购物车

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/cart/items` | 获取购物车列表 |
| POST | `/api/cart/items` | 加入购物车 |
| PUT | `/api/cart/items/{id}/quantity` | 更新数量（≤0 自动删除） |
| DELETE | `/api/cart/items/{id}` | 删除单项 |
| DELETE | `/api/cart/items` | 清空购物车 |

### 订单

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/orders` | 创建订单 |
| GET | `/api/orders/user` | 当前用户订单列表 |
| GET | `/api/orders/{id}` | 订单详情 |
| PUT | `/api/orders/{id}/status?status=` | 更新订单状态 |
| PUT | `/api/orders/{id}/cancel` | 取消订单 |

### 用户

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/user/me` | 当前用户信息 |
| PUT | `/api/user/profile` | 更新个人资料 |
| PUT | `/api/user/avatar` | 上传头像 |
| GET | `/api/user/books` | 我的在售图书 |

### 消息

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/chat/history?bookId=` | 聊天记录 |
| GET | `/api/chat/list` | 聊天列表 |
| POST | `/api/chat/bargain/handle` | 处理议价（接受/拒绝） |
| WS | `/ws/chat?token=&bookId=&sellerId=` | WebSocket 实时通讯 |

## 高可用与保护机制

- **网关层**：Sentinel 网关流控，规则持久化到 Nacos，重启不丢失
- **服务层**：`@SentinelResource` 注解 + BlockHandler，核心接口均有降级兜底
- **调用层**：`feign.sentinel.enabled: true`，配合 FallbackFactory 实现远程调用熔断
- **WebSocket**：`ConcurrentHashMap` 管理在线会话，支持多实例扩展（消息广播仅限当前节点，多实例可升级为 Redis Pub/Sub）
- **缓存穿透保护**：查询 DB 后回写 Redis，避免空值穿透
- **连接池**：Lettuce 连接池（max-active=10, min-idle=1），Tomcat 线程池限制（max-threads=25）

## 许可证

本项目仅用于学习与竞赛目的，不用于商业用途。详见 [LICENSE](LICENSE)。
