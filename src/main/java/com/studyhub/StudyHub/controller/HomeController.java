package com.studyhub.StudyHub.controller;


import com.studyhub.StudyHub.dto.ChatDTOs; // <-- THÊM
import com.studyhub.StudyHub.dto.CommentDto;
import com.studyhub.StudyHub.dto.PostDto;
import com.studyhub.StudyHub.entity.Post;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.ChatService; // <-- THÊM
import com.studyhub.StudyHub.service.PostService;
import com.studyhub.StudyHub.service.PresenceService; // <-- THÊM
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.util.ArrayList; // <-- THÊM
import java.util.HashSet; // <-- THÊM
import java.util.List;
import java.util.Set; // <-- THÊM

@Controller
public class HomeController {

    @Autowired private PostService postService;
    @Autowired private UserRepository userRepository;
    @Autowired private ChatService chatService; // <-- THÊM
    @Autowired private PresenceService presenceService; // <-- THÊM

    @GetMapping("/")
    public String home(Model model, Principal principal) {

        // === THÊM MỚI: Chuẩn bị danh sách liên hệ và online ===
        List<ChatDTOs.ChatRoomDto> contacts = new ArrayList<>();
        Set<String> onlineUsers = new HashSet<>();
        // === KẾT THÚC THÊM MỚI ===

        // Lấy thông tin user hiện tại (để biết ai đang like)
        if (principal != null) {
            User currentUser = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).get();
            model.addAttribute("currentUserId", currentUser.getId());

            // === THÊM MỚI: Lấy dữ liệu nếu đã login ===
            contacts = chatService.getChatRooms(currentUser);
            onlineUsers = presenceService.getOnlineUsers();
            // === KẾT THÚC THÊM MỚI ===

        } else {
            model.addAttribute("currentUserId", 0L);
        }

        model.addAttribute("pageTitle", "Trang Chủ");

        // Gửi 1 CommentDto rỗng để form bình luận hoạt động
        model.addAttribute("commentDto", new CommentDto());

        // Lấy tất cả bài đăng
        List<Post> posts = postService.getAllPostsSortedByDate();
        model.addAttribute("posts", posts);

        // === THÊM MỚI: Gửi danh sách liên hệ ra view ===
        model.addAttribute("contacts", contacts);
        model.addAttribute("onlineUsers", onlineUsers);
        // === KẾT THÚC THÊM MỚI ===

        return "index";
    }

    // === (Giữ nguyên hàm chatPage) ===
    @GetMapping("/chat")
    public String chatPage(Model model, Principal principal) {
        model.addAttribute("pageTitle", "Chat Realtime");

        // Lấy email (hoặc username) từ principal
        String usernameOrEmail = principal.getName();

        // SỬA LỖI: Tìm bằng findByUsernameOrEmail
        User currentUser = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("Lỗi nghiêm trọng: Không tìm thấy user đã đăng nhập"));

        // Gửi thông tin user hiện tại
        model.addAttribute("currentUserId", currentUser.getId());
        model.addAttribute("currentUsername", currentUser.getUsername());

        return "chat"; // Trả về file chat.html
    }
}