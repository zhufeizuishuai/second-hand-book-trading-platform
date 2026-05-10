package org.example.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.example.po.Book;
import org.example.service.BookService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/books")
public class BookController {
    @Autowired
    private BookService bookService;

    // 获取推荐书籍
    @GetMapping("/recommended")
    public ResponseEntity<List<Book>> getRecommendedBooks(@RequestParam(required = false) String school) {
        return bookService.getRecommendedBooks(school);
    }

    // 搜索书籍
    @GetMapping("/search")
    public ResponseEntity<?> searchBooks(@RequestParam String q, @RequestParam(required = false) String sort, @RequestParam(required = false) String school) {
        return bookService.searchBooks(q, sort, school);
    }

    // 获取书籍详情
    @SentinelResource(value = "getBookDetails", blockHandler = "getBookDetailsBlock")
    @GetMapping("/{id}")
    public ResponseEntity<Book> getBookDetails(@PathVariable Long id) {
        return bookService.getBookDetails(id);
    }

    // 上传书籍
    @SentinelResource(value = "uploadBook", blockHandler = "uploadBookBlock")
    @PostMapping()
    public ResponseEntity<?> uploadBook(@RequestBody Book book) {
       return  bookService.uploadBook(book);
    }

    // 获取用户发布的书籍
    @GetMapping("/user/{sellerId}")
    public ResponseEntity<List<Book>> getUserBooks(@PathVariable Long sellerId) {
        return bookService.getBySellerId(sellerId);
    }

    // 上传文件到OSS
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        return bookService.uploadFile(file);
    }

    // AI 识别接口
    @PostMapping("/ai-recognize")
    public ResponseEntity<?> aiRecognize(@RequestBody Map<String, Object> request) {
        return bookService.aiRecognize(request);
    }

    // === Sentinel block handlers ===
    public ResponseEntity<Book> getBookDetailsBlock(Long id, BlockException ex) {
        return ResponseEntity.status(429).body(null);
    }

    public ResponseEntity<?> uploadBookBlock(Book book, BlockException ex) {
        return ResponseEntity.status(429).body(Map.of("code", 429, "message", "书籍上传接口繁忙，请稍后再试"));
    }
}