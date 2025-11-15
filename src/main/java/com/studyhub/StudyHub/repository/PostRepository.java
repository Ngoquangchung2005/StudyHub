package com.studyhub.StudyHub.repository;

import com.studyhub.StudyHub.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

// Sắp xếp các Post theo thứ tự mới nhất
import org.springframework.data.domain.Sort;
import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    // Tự động tạo câu lệnh SELECT * FROM posts ORDER BY createdAt DESC
    List<Post> findAll(Sort sort);
}