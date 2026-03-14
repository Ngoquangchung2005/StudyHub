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
// ... import cũ
import org.springframework.web.bind.annotation.RequestParam; // <-- Thêm import

import java.security.Principal;
import java.util.ArrayList; // <-- THÊM
import java.util.HashSet; // <-- THÊM
import java.util.List;
import java.util.Set; // <-- THÊM

@Controller
public class HomeController {

    @Autowired private PostService postService;
    @Autowired private UserRepository userRepository;
    @Autowired private ChatService chatService;
    @Autowired private PresenceService presenceService;

    @GetMapping("/")
    public String home(Model model,
                       Principal principal,
                       @RequestParam(name = "keyword", required = false) String keyword) {


        List<ChatDTOs.ChatRoomDto> contacts = new ArrayList<>();
        Set<String> onlineUsers = new HashSet<>();

        if (principal != null) {
            User currentUser = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).get();
            model.addAttribute("currentUserId", currentUser.getId());
            contacts = chatService.getChatRooms(currentUser);
            onlineUsers = presenceService.getOnlineUsers();
        } else {
            model.addAttribute("currentUserId", 0L);
        }

        // Xử lý danh sách bài đăng
        List<Post> posts;
        if (keyword != null && !keyword.isEmpty()) {
            posts = postService.searchPosts(keyword);
            model.addAttribute("keyword", keyword);
            model.addAttribute("pageTitle", "Tìm kiếm: " + keyword);
        } else {
            posts = postService.getAllPostsSortedByDate();
            model.addAttribute("pageTitle", "Trang Chủ");
        }

        model.addAttribute("posts", posts);

        // 3. Các model attribute khác (Giữ nguyên)
        model.addAttribute("commentDto", new CommentDto());
        model.addAttribute("contacts", contacts);
        model.addAttribute("onlineUsers", onlineUsers);

        return "index";
    }


    @GetMapping("/chat")
    public String chatPage(Model model, Principal principal) {
        model.addAttribute("pageTitle", "Chat Realtime");


        String usernameOrEmail = principal.getName();


        User currentUser = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("Lỗi nghiêm trọng: Không tìm thấy user đã đăng nhập"));

        // Gửi thông tin user hiện tại
        model.addAttribute("currentUserId", currentUser.getId());
        model.addAttribute("currentUsername", currentUser.getUsername());

        return "chat";
    }
}