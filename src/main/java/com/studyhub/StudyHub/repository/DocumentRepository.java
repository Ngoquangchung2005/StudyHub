package com.studyhub.StudyHub.repository;




import com.studyhub.StudyHub.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}