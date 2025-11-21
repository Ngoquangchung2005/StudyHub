package com.studyhub.StudyHub.service.iml;

import com.studyhub.StudyHub.entity.Document;
import com.studyhub.StudyHub.repository.DocumentRepository;
import com.studyhub.StudyHub.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DocumentServiceImpl implements DocumentService {

    @Autowired
    private DocumentRepository documentRepository;

    @Override
    public List<Document> getAllDocuments() {
        return documentRepository.findAll(Sort.by(Sort.Direction.DESC, "uploadedAt"));
    }

    @Override
    public List<Document> searchDocuments(String keyword, Long categoryId) {
        return documentRepository.searchDocuments(keyword, categoryId, Sort.by(Sort.Direction.DESC, "uploadedAt"));
    }

    @Override
    public Document getDocumentById(Long id) {
        return documentRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public void incrementDownloadCount(Long documentId) {
        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc != null) {
            doc.setDownloads(doc.getDownloads() + 1);
            documentRepository.save(doc);
        }
    }
}