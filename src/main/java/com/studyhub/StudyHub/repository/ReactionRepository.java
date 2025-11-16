package com.studyhub.StudyHub.repository;



import com.studyhub.StudyHub.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {
    // Dùng để kiểm tra user đã like bài post này chưa
    Optional<Reaction> findByPostIdAndUserId(Long postId, Long userId);
}
