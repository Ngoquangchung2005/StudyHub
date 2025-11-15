package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.dto.PostDto; // THÊM DÒNG NÀY
import com.studyhub.StudyHub.entity.Post;
import com.studyhub.StudyHub.service.PostService; // THÊM DÒNG NÀY
import org.springframework.beans.factory.annotation.Autowired; // THÊM DÒNG NÀY
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute; // THÊM DÒNG NÀY
import org.springframework.web.bind.annotation.PostMapping; // THÊM DÒNG NÀY
import org.springframework.web.servlet.mvc.support.RedirectAttributes; // THÊM DÒNG NÀY
import com.studyhub.StudyHub.entity.User; // THÊM DÒNG NÀY
import com.studyhub.StudyHub.repository.UserRepository; // THÊM DÒNG NÀY
import java.util.List; // THÊM DÒNG NÀY

import java.security.Principal; // THÊM DÒNG NÀY
import java.util.List;

@Controller
public class HomeController {

    // --- TIÊM SERVICE VÀO ---
    @Autowired
    private PostService postService;
    @Autowired
    private UserRepository userRepository;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("pageTitle", "Trang Chủ - StudyHub");

        // --- THÊM DÒNG NÀY ---
        // Thêm một đối tượng PostDto rỗng để "kết dính" với form
        model.addAttribute("postDto", new PostDto());
        // 1. Lấy tất cả bài đăng từ service
        List<Post> posts = postService.getAllPostsSortedByDate();
        // 2. Gửi danh sách 'posts' ra ngoài view
        model.addAttribute("posts", posts);

        return "index";

    }
    @GetMapping("/chat")
    public String showChatPage(Model model, Principal principal) {

        // 1. Lấy thông tin User hiện tại
        String username = principal.getName();
        User currentUser = userRepository.findByUsernameOrEmail(username, username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

        // 2. Lấy tất cả User khác (để hiển thị danh sách chat)
        List<User> allUsers = userRepository.findAll();

        // 3. Gửi sang cho View (chat.html)
        model.addAttribute("currentUser", currentUser); // User hiện tại
        model.addAttribute("allUsers", allUsers); // Toàn bộ user
        model.addAttribute("pageTitle", "Chat Realtime");

        return "chat"; // Trả về file 'chat.html'
    }

    // --- HÀM MỚI: XỬ LÝ VIỆC TẠO BÀI ĐĂNG ---
    @PostMapping("/posts/create")
    public String handleCreatePost(@ModelAttribute("postDto") PostDto postDto,
                                   Principal principal, // Lấy thông tin user đã đăng nhập
                                   RedirectAttributes redirectAttributes) {

        if (principal == null) {
            // Chưa đăng nhập, không cho đăng bài
            return "redirect:/login";
        }

        // Lấy username (hoặc email) của người đang đăng nhập
        String username = principal.getName();

        // Gọi service để tạo bài đăng
        postService.createPost(postDto, username);

        // Gửi thông báo thành công
        redirectAttributes.addFlashAttribute("successMessage", "Đăng bài thành công!");

        return "redirect:/"; // Chuyển hướng về trang chủ
    }
}