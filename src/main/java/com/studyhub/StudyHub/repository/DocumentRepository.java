package com.studyhub.StudyHub.repository;

import com.studyhub.StudyHub.entity.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional; // <-- Thêm import này

public interface DocumentRepository extends JpaRepository<Document, Long> {

    // Hàm này dùng để kiểm tra file khi người dùng bấm Tải xuống
    Optional<Document> findByStoragePath(String storagePath);


    @Query("SELECT d FROM Document d " +
            "LEFT JOIN FETCH d.user " +
            "LEFT JOIN FETCH d.category " +
            "WHERE d.isPublic = true " + // <-- Đã lọc chỉ file công khai
            "AND (:categoryId IS NULL OR d.category.id = :categoryId) " +
            "AND (:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.fileName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.tags) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Document> searchDocuments(@Param("keyword") String keyword,
                                   @Param("categoryId") Long categoryId,
                                   Sort sort);

    List<Document> findByCategoryId(Long categoryId);
}