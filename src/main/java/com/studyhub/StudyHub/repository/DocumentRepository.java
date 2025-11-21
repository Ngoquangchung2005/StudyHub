package com.studyhub.StudyHub.repository;

import com.studyhub.StudyHub.entity.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    // === SỬA QUERY NÀY ===
    // Thêm "LEFT JOIN FETCH d.user" và "LEFT JOIN FETCH d.category"
    @Query("SELECT d FROM Document d " +
            "LEFT JOIN FETCH d.user " +       // <-- Lấy luôn User
            "LEFT JOIN FETCH d.category " +   // <-- Lấy luôn Category
            "WHERE d.isPublic = true " +
            "AND (:categoryId IS NULL OR d.category.id = :categoryId) " +
            "AND (:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.fileName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.tags) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Document> searchDocuments(@Param("keyword") String keyword,
                                   @Param("categoryId") Long categoryId,
                                   Sort sort);
}