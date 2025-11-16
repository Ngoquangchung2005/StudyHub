package com.studyhub.StudyHub.repository;

import com.studyhub.StudyHub.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
}