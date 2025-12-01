package com.studyhub.StudyHub.controller.admin;

import com.studyhub.StudyHub.entity.Post;
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
import com.studyhub.StudyHub.repository.CategoryRepository; // <-- Thêm import
import org.springframework.web.bind.annotation.RequestParam; // <-- Thêm import

import java.util.List;

@Controller
@RequestMapping("/admin/posts")
public class AdminPostController {

    @Autowired private PostRepository postRepository;
    @Autowired private PostService postService;
    @Autowired private CategoryRepository categoryRepository; // <-- Inject thêm cái này

    @GetMapping
    public String listPosts(Model model,
                            @RequestParam(name = "keyword", required = false) String keyword,
                            @RequestParam(name = "categoryId", required = false) Long categoryId) {

        List<Post> posts;
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        // Logic tìm kiếm
        if ((keyword != null && !keyword.isEmpty()) || categoryId != null) {
            posts = postRepository.searchPostsForAdmin(keyword, categoryId, sort);
        } else {
            // Mặc định lấy hết
            posts = postRepository.findAll(sort);
        }

        model.addAttribute("posts", posts);

        // Gửi dữ liệu để hiển thị lại trên Form tìm kiếm
        model.addAttribute("categories", categoryRepository.findAll());
        model.addAttribute("keyword", keyword);
        model.addAttribute("categoryId", categoryId);

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