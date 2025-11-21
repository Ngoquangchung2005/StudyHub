package com.studyhub.StudyHub.service;

import com.studyhub.StudyHub.entity.Document;
import java.util.List;

public interface DocumentService {
    List<Document> getAllDocuments();
    List<Document> searchDocuments(String keyword, Long categoryId);
    Document getDocumentById(Long id);
    void incrementDownloadCount(Long documentId); // Tăng lượt tải
}