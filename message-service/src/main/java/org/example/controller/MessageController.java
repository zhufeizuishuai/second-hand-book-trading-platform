package org.example.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.example.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class MessageController {

    @Autowired
    private MessageService messageService;

    @GetMapping("/history")
    public ResponseEntity<?> getChatHistory(@RequestParam String bookId) {
        return messageService.getHistory(bookId);
    }

    @GetMapping("/list")
    public ResponseEntity<?> getChatList() {
        return messageService.getChatList();
    }

    @SentinelResource(value = "handleBargain", blockHandler = "handleBargainBlock")
    @PostMapping("/bargain/handle")
    public ResponseEntity<?> handleBargain(@RequestBody Map<String, Object> request) {
        return messageService.handleBargain(request);
    }

    // === Sentinel block handlers ===
    public ResponseEntity<?> handleBargainBlock(Map<String, Object> request, BlockException ex) {
        return ResponseEntity.status(429).body(Map.of("code", 429, "message", "砍价接口繁忙，请稍后再试"));
    }
}