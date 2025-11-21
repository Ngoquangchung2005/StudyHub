package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.entity.Category;
import com.studyhub.StudyHub.entity.Document;
import com.studyhub.StudyHub.repository.CategoryRepository;
import com.studyhub.StudyHub.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class DocumentLibraryController {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private CategoryRepository categoryRepository;

    @GetMapping("/documents")
    public String showDocumentLibrary(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            Model model) {

        // 1. Lấy danh sách Categories để hiển thị bộ lọc
        List<Category> categories = categoryRepository.findAll();

        // 2. Tìm kiếm documents
        List<Document> documents = documentService.searchDocuments(keyword, categoryId);

        // 3. Đẩy dữ liệu ra View
        model.addAttribute("documents", documents);
        model.addAttribute("categories", categories);
        model.addAttribute("keyword", keyword);
        model.addAttribute("currentCategoryId", categoryId);
        model.addAttribute("pageTitle", "Kho tài liệu");

        return "documents"; // Trả về file documents.html
    }
}