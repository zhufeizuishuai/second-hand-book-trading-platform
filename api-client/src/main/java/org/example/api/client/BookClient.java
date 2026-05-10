package org.example.api.client;

import org.example.api.client.fallback.BookClientFallbackFactory;
import org.example.api.po.Book;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@FeignClient(name = "book-service", fallbackFactory = BookClientFallbackFactory.class)
public interface BookClient{
    @GetMapping("/api/books/{id}")
    Book getById(@PathVariable("id") Long bookId);

    @GetMapping("/api/books/user/{sellerId}")
    ResponseEntity<List<Book>> getUserBooks(@PathVariable("sellerId") Long id);

}

