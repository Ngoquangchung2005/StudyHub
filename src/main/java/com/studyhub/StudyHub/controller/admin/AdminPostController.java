package com.studyhub.StudyHub.controller.admin;

import com.studyhub.StudyHub.repository.PostRepository;
import com.studyhub.StudyHub.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/posts")
public class AdminPostController {

    @Autowired private PostRepository postRepository;
    @Autowired private PostService postService; // Tận dụng hàm xóa của Service (đã có cascade)

    @GetMapping
    public String listPosts(Model model) {
        // Lấy tất cả bài viết (kể cả private) để admin kiểm duyệt
        model.addAttribute("posts", postRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("pageTitle", "Quản lý Bài viết");
        return "admin/posts";
    }

    @PostMapping("/{id}/delete")
    public String deletePost(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            // Admin xóa thì principal null cũng được, hoặc ta cần sửa Service để bỏ qua check owner nếu là Admin
            // Cách nhanh nhất ở đây: Dùng Repository xóa trực tiếp hoặc ép kiểu
            postRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa bài viết/tài liệu vi phạm.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi xóa bài: " + e.getMessage());
        }
        return "redirect:/admin/posts";
    }
}