package org.example.service;


import com.baomidou.mybatisplus.extension.service.IService;
import org.example.po.Message;
import org.springframework.http.ResponseEntity;

import java.util.Map;

public interface MessageService extends IService<Message> {
    ResponseEntity<?> getHistory(String bookId);

    ResponseEntity<?> getChatList();

    ResponseEntity<?> handleBargain(Map<String, Object> request);
}
