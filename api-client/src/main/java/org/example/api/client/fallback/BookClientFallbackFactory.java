package org.example.api.client.fallback;

import lombok.extern.slf4j.Slf4j;
import org.example.api.client.BookClient;
import org.example.api.po.Book;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class BookClientFallbackFactory implements FallbackFactory<BookClient> {

    @Override
    public BookClient create(Throwable cause) {
        log.error("book-service 调用失败，触发 Sentinel 降级", cause);
        return new BookClient() {
            @Override
            public Book getById(Long bookId) {
                return null;
            }

            @Override
            public ResponseEntity<List<Book>> getUserBooks(Long id) {
                return ResponseEntity.ok(Collections.emptyList());
            }
        };
    }
}
