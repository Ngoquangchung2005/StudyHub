package com.studyhub.StudyHub.controller.admin;

import com.studyhub.StudyHub.entity.Category;
import com.studyhub.StudyHub.entity.Document;
import com.studyhub.StudyHub.repository.CategoryRepository;
import com.studyhub.StudyHub.repository.DocumentRepository; // <-- Import thêm
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private DocumentRepository documentRepository;

    // 1. Hiển thị danh sách
    @GetMapping
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("newCategory", new Category());
        model.addAttribute("pageTitle", "Quản lý Danh mục");
        return "admin/categories";
    }

    // 2. Thêm mới HOẶC Cập nhật
    @PostMapping("/save")
    public String saveCategory(@ModelAttribute Category category, RedirectAttributes redirectAttributes) {

        categoryRepository.save(category);

        String msg = (category.getId() != null) ? "Cập nhật thành công!" : "Tạo mới thành công!";
        redirectAttributes.addFlashAttribute("successMessage", msg);
        return "redirect:/admin/categories";
    }

    // 3. Xóa danh mục
    @PostMapping("/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            //  Tìm tất cả tài liệu đang thuộc danh mục này
            List<Document> docs = documentRepository.findByCategoryId(id);

            // Gỡ bỏ danh mục khỏi tài liệu (Set null)
            for (Document doc : docs) {
                doc.setCategory(null);
                documentRepository.save(doc);
            }

            // Xóa danh mục (Lúc này danh mục đã trống, xóa thoải mái)
            categoryRepository.deleteById(id);

            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa danh mục. Các tài liệu cũ đã chuyển sang trạng thái 'Chưa phân loại'.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi khi xóa: " + e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    // 4. Xem chi tiết danh mục
    @GetMapping("/{id}")
    public String viewCategoryDetails(@PathVariable Long id, Model model) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy danh mục"));

        List<Document> documents = documentRepository.findByCategoryId(id);

        model.addAttribute("category", category);
        model.addAttribute("documents", documents);
        model.addAttribute("pageTitle", "Chi tiết: " + category.getName());

        return "admin/category-details";
    }
}