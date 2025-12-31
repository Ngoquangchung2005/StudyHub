package com.studyhub.StudyHub.repository;

import com.studyhub.StudyHub.entity.Post;
import com.studyhub.StudyHub.entity.User;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {


    @Query("SELECT p FROM Post p " +
            "LEFT JOIN FETCH p.user " +
            "LEFT JOIN FETCH p.documents " +
            "LEFT JOIN FETCH p.comments c " +
            "LEFT JOIN FETCH c.user " +
            "LEFT JOIN FETCH p.reactions " +
            "WHERE p.isPublic = true") // <-- CHỈ LẤY BÀI CÔNG KHAI
    List<Post> findAllWithDetails(Sort sort);



    @Query("SELECT DISTINCT p FROM Post p " +
            "LEFT JOIN FETCH p.user " +
            "LEFT JOIN FETCH p.documents d " +
            "LEFT JOIN FETCH p.comments c " +
            "LEFT JOIN FETCH c.user " +
            "LEFT JOIN FETCH p.reactions " +
            "WHERE p.isPublic = true AND " + // <-- CHỈ TÌM BÀI CÔNG KHAI
            "(:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.fileName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.tags) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Post> searchPosts(@Param("keyword") String keyword, Sort sort);


    // Hàm lấy bài theo User
    @Query("SELECT p FROM Post p " +
            "LEFT JOIN FETCH p.user " +
            "LEFT JOIN FETCH p.documents " +
            "LEFT JOIN FETCH p.comments c " +
            "LEFT JOIN FETCH c.user " +
            "LEFT JOIN FETCH p.reactions " +
            "WHERE p.user = :user")
    List<Post> findAllByUserWithDetails(@Param("user") User user, Sort sort);

    // Lấy danh sách bài đăng CÔNG KHAI của một user cụ thể
    @Query("SELECT p FROM Post p " +
            "LEFT JOIN FETCH p.user " +
            "LEFT JOIN FETCH p.documents " +
            "LEFT JOIN FETCH p.comments c " +
            "LEFT JOIN FETCH c.user " +
            "LEFT JOIN FETCH p.reactions " +
            "WHERE p.user = :user AND p.isPublic = true") // <-- Thêm điều kiện isPublic = true
    List<Post> findPublicByUserWithDetails(@Param("user") User user, Sort sort);


    // Tìm kiếm bài viết (Không phân biệt public/private) theo từ khóa và danh mục
    @Query("SELECT DISTINCT p FROM Post p " +
            "LEFT JOIN FETCH p.user " +
            "LEFT JOIN FETCH p.documents d " +
            "LEFT JOIN d.category c " + // Join với category thông qua document
            "WHERE (:categoryId IS NULL OR c.id = :categoryId) " +
            "AND (:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(d.fileName) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<Post> searchPostsForAdmin(@Param("keyword") String keyword,
                                   @Param("categoryId") Long categoryId,
                                   Sort sort);
}