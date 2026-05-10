package org.example.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.example.po.Book;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface BookService extends IService<Book> {
    ResponseEntity<List<Book>> getRecommendedBooks(String school);

    ResponseEntity<Book> getBookDetails(Long id);

    ResponseEntity<?> searchBooks(String q, String sort, String school);

    ResponseEntity<?> uploadFile(MultipartFile file);

    ResponseEntity<?> aiRecognize(Map<String, Object> request);

    ResponseEntity<?> uploadBook(Book book);

    ResponseEntity<List<Book>> getBySellerId(Long sellerId);
}
