package org.example.serviceImpl;

import cn.hutool.json.JSONUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.baidu.aip.ocr.AipOcr;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.example.api.client.UserClient;
import org.example.api.po.User;
import org.example.mapper.BookMapper;
import org.example.po.Book;
import org.example.service.BookService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BookServiceImpl extends ServiceImpl<BookMapper, Book> implements BookService {
    // OSS配置
    @Value("${aliyun.oss.endpoint}")
    private String endpoint;
    @Value("${aliyun.oss.bucketName}")
    private String bucketName;
    @Value("${aliyun.oss.accessKeyId}")
    private String accessKeyId;
    @Value("${aliyun.oss.accessKeySecret}")
    private String accessKeySecret;



    private final UserClient userClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final BookMapper bookMapper;
    // 百度 OCR 客户端
    private AipOcr client;
    // 初始化百度 OCR 客户端
    private void init(){
        // 百度 OCR API 配置
        String appId = "your-baidu-ocr-app-id";
        String apiKey = "your-baidu-ocr-api-key";
        String secretKey = "your-baidu-ocr-secret-key";
        // 初始化客户端
        client = new AipOcr(appId, apiKey, secretKey);
        // 设置请求参数
        client.setConnectionTimeoutInMillis(2000);
        client.setSocketTimeoutInMillis(60000);
    }
    @Override
    public ResponseEntity<List<Book>> getRecommendedBooks(String school) {
        List<Book> books = bookMapper.selectList(null);
        // 按照销量降序排序
        books.sort((b1, b2) -> b2.getSales() - b1.getSales());
        // 根据学校筛选（如果提供了学校参数）
        if (school != null && !school.isEmpty()) {
            books = books.stream()
                    .filter(book -> book.getSchool() != null && (book.getSchool().equals(school) || book.getSchool().contains(school)))
                    .collect(java.util.stream.Collectors.toList());
        }
        return ResponseEntity.ok(books);
    }

    @Override
    public ResponseEntity<Book> getBookDetails(Long id) {
//        Optional<Book> bookOptional = Optional.ofNullable(bookMapper.selectById(id));
//        if (bookOptional.isPresent()) {
//            return ResponseEntity.ok(bookOptional.get());
//        } else {
//            return ResponseEntity.notFound().build();
//        }
        String bookJson = stringRedisTemplate.opsForValue().get("book:" + id);
        Book book;
        if(bookJson != null){
             book = JSONUtil.toBean(bookJson, Book.class);
        }
        else{
        book = bookMapper.selectById(id);
        stringRedisTemplate.opsForValue().set("book:" + id, JSONUtil.toJsonStr(book));
        }
        if (book != null) {
            return ResponseEntity.ok(book);
        } else {
            return ResponseEntity.notFound().build();
        }

    }

    @Override
    public ResponseEntity<?> searchBooks(String q, String sort, String school) {
        LambdaQueryWrapper<Book> queryWrapper = new LambdaQueryWrapper<>();
        if (q != null && !q.isBlank()) {
            queryWrapper.like(Book::getTitle, q);
        }

        // ===== 2. 学校筛选（为空不拼接）=====
        if (school != null && !school.isBlank()) {
            queryWrapper.eq(Book::getSchool, school);
        }
        if(sort != null){
            switch (sort) {
                case "price_asc":
                    queryWrapper.orderByAsc(Book::getPrice);
                    break;
                case "price_desc":
                    queryWrapper.orderByDesc(Book::getPrice);
                    break;
                case "sales_asc":
                    queryWrapper.orderByAsc(Book::getSales);
                    break;
                case "sales_desc":
                    queryWrapper.orderByDesc(Book::getSales);
                    break;
                case "distance_asc":
                    queryWrapper.orderByAsc(Book::getDistance);
                    break;
                case "distance_desc":
                queryWrapper.orderByDesc(Book::getDistance);
                break;
                default:
            }
        }
        List<Book> books = bookMapper.selectList(queryWrapper);
        return ResponseEntity.ok(books);
    }

    @Override
    public ResponseEntity<?> uploadFile(MultipartFile file) {
        OSS ossClient = null;
        init();
        try {
            // 创建OSS客户端
            ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                    : ".jpg";
            String fileName = "books/" + UUID.randomUUID().toString() + fileExtension;

            // 上传文件到OSS
            ossClient.putObject(bucketName, fileName, file.getInputStream());

            // 返回文件URL
            String fileUrl = "https://" + bucketName + "." + endpoint + "/" + fileName;
            return ResponseEntity.ok(fileUrl);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("文件上传失败");
        } finally {
            // 关闭OSS客户端
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    @Override
    public ResponseEntity<?> aiRecognize(Map<String, Object> request) {
        try {
            // 获取上传的图片 URL（来自 OSS）
            String imageUrl = (String) request.get("image");
            if (imageUrl == null || imageUrl.isEmpty()) {
                // 即使图片 URL 为空，也返回包含 description 字段的结果
                Map<String, Object> result = new HashMap<>();
                result.put("title", "");
                result.put("author", "");
                result.put("isbn", "");
                result.put("description", "由 AI 自动识别生成");
                return ResponseEntity.badRequest().body(result);
            }

            // 调用百度 OCR API 识别文字
            HashMap<String, String> options = new HashMap<>();
            options.put("language_type", "CHN_ENG");
            options.put("detect_direction", "true");
            options.put("detect_language", "true");
            options.put("probability", "true");

            JSONObject response = client.basicGeneralUrl(imageUrl, options);

            // 解析识别结果
            String title = "";
            String author = "";
            String isbn = "";
            StringBuilder allText = new StringBuilder();

            if (response != null && response.has("words_result")) {
                JSONArray wordsResult = response.getJSONArray("words_result");
                for (int i = 0; i < wordsResult.length(); i++) {
                    JSONObject word = wordsResult.getJSONObject(i);
                    String text = word.getString("words");
                    // 收集所有识别到的文本
                    allText.append(text).append(" ");

                    // 提取 ISBN
                    if (isbn.isEmpty() && (text.contains("ISBN") || text.matches(".*\\d{13}.*"))) {
                        isbn = text.replaceAll("[^0-9]", "").trim();
                        if (isbn.length() >= 13) {
                            isbn = isbn.substring(0, 13);
                        }
                    }
                    // 提取作者（通常包含"著"、"作者"等关键词）
                    else if (author.isEmpty() && (text.contains("著") || text.contains("作者") || text.contains("编"))) {
                        author = text.replaceAll("[著作者编]", "").trim();
                        // 过滤乱码：只保留中文字符和英文字母
                        author = author.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z ]", "");
                        // 如果过滤后为空，使用默认值
                        if (author.isEmpty()) {
                            author = "未知作者";
                        }
                    }
                    // 提取书名（假设第一个非作者、非 ISBN 的文本为书名）
                    else if (title.isEmpty() && !text.contains("著") && !text.contains("作者") && !text.contains("编") && !text.contains("ISBN")) {
                        title = text.trim();
                        // 过滤乱码：只保留中文字符和英文字母
                        title = title.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z ]", "");
                        // 如果过滤后为空，使用默认值
                        if (title.isEmpty()) {
                            title = "未知书名";
                        }
                    }
                }
            }

            // 生成详细说明
            String description = "由 AI 自动识别生成";
            if (!title.isEmpty() && !author.isEmpty()) {
                description = "《" + title + "》是由" + author + "创作的书籍。";
                if (!isbn.isEmpty()) {
                    description += " ISBN：" + isbn + "。";
                }
                if (allText.length() > 0) {
                    description += " 识别到的其他信息：" + allText.toString().substring(0, Math.min(allText.length(), 100)) + (allText.length() > 100 ? "..." : "");
                }
            }

            // 构造返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("title", title.isEmpty() ? "未知书名" : title);
            result.put("author", author.isEmpty() ? "未知作者" : author);
            result.put("isbn", isbn);
            result.put("description", description);
            System.out.println(result);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            // 即使发生异常，也返回包含 description 字段的结果
            Map<String, Object> result = new HashMap<>();
            result.put("title", "");
            result.put("author", "");
            result.put("isbn", "");
            result.put("description", "由 AI 自动识别生成");
            return ResponseEntity.badRequest().body(result);
        }
    }

    @Override
    public ResponseEntity<?> uploadBook(Book book) {
        // 获取当前登录用户
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return ResponseEntity.badRequest().body("用户未登录，请先登录后再上传书籍");
        }
        // 设置书籍的卖家
        book.setSeller(currentUser);
        book.setSellerId(currentUser.getId());
        boolean save = save(book);
        if(save){
            stringRedisTemplate.opsForValue().set("book:" + book.getId(), JSONUtil.toJsonStr(book));
            return ResponseEntity.ok(book);
        }
        else
        {
            return ResponseEntity.badRequest().body("上传书籍失败");
        }
    }

    @Override
    public ResponseEntity<List<Book>> getBySellerId(Long sellerId) {
        List<Book> books = list(new QueryWrapper<Book>().eq("seller_id", sellerId));
        return ResponseEntity.ok(books);
    }

    // 获取当前登录用户
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        String username = authentication.getName();
        return userClient.findByUsername(username);
    }
}
