package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.dto.ChatDTOs;
import com.studyhub.StudyHub.entity.Friendship;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.FriendService;
import com.studyhub.StudyHub.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class FriendController {

    @Autowired private FriendService friendService;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;

    // ==========================================
    // 1. CONTROLLER TRẢ VỀ GIAO DIỆN (HTML)
    // ==========================================

    @GetMapping("/friends")
    public String viewFriendsPage(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";

        // --- FIX: Handle case where user is not found in DB ---
        var userOpt = userRepository.findByUsername(principal.getName());
        if (userOpt.isEmpty()) {
            return "redirect:/logout"; // Force logout if user not found
        }
        User user = userOpt.get();
        // -----------------------------------------------------

        // Lấy danh sách bạn bè
        List<User> friends = friendService.getFriendList(user.getId());

        // Lấy danh sách lời mời kết bạn đang chờ (Mình là người nhận)
        List<Friendship> pendingRequests = friendService.getPendingRequests(user.getId());

        model.addAttribute("friends", friends);
        model.addAttribute("pendingRequests", pendingRequests);

        return "friends";
    }
    // Xử lý nút "Chấp nhận" từ Form HTML
    @PostMapping("/friends/accept/{id}")
    public String acceptRequest(@PathVariable Long id, Principal principal, RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(principal.getName()).orElseThrow();
        try {
            friendService.acceptFriendRequest(id, user.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Đã chấp nhận kết bạn!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/friends"; // Load lại trang
    }

    // Xử lý nút "Xóa/Từ chối" từ Form HTML (Bạn cần thêm hàm decline trong Service nếu chưa có, hoặc dùng hàm delete)
    @PostMapping("/friends/decline/{id}")
    public String declineRequest(@PathVariable Long id, Principal principal) {
        // Logic từ chối: Xóa bản ghi Friendship
        // Ở đây mình tạm dùng repository thông qua service hoặc gọi hàm unfriend/delete tương tự
        // Giả sử bạn dùng chung logic xóa
        try {
            friendService.declineFriendRequest(id); // Bạn cần đảm bảo Service có hàm này
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/friends";
    }

    // Xử lý nút "Hủy kết bạn" từ Form HTML
    @PostMapping("/friends/unfriend/{friendId}")
    public String unfriend(@PathVariable Long friendId, Principal principal, RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(principal.getName()).orElseThrow();
        try {
            friendService.unfriend(user.getId(), friendId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy kết bạn.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Lỗi: " + e.getMessage());
        }
        return "redirect:/friends";
    }

    // ==========================================
    // 2. API (TRẢ VỀ JSON CHO JAVASCRIPT/AJAX)
    // ==========================================

    // API: Gửi lời mời kết bạn (Dùng cho nút "Kết bạn" ở trang Search hoặc Profile)
    @PostMapping("/api/friends/request/{userId}")
    @ResponseBody
    public ResponseEntity<?> sendFriendRequestApi(@PathVariable Long userId, Principal principal) {
        User requester = userRepository.findByUsername(principal.getName()).orElseThrow();
        try {
            friendService.sendFriendRequest(requester.getId(), userId);
            return ResponseEntity.ok("Gửi lời mời thành công");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // API: Chấp nhận kết bạn (Dùng cho trang Profile nếu bấm nút Chấp nhận bằng JS)
    @PostMapping("/api/friends/accept/{friendshipId}")
    @ResponseBody
    public ResponseEntity<?> acceptFriendRequestApi(@PathVariable Long friendshipId, Principal principal) {
        User user = userRepository.findByUsername(principal.getName()).orElseThrow();
        try {
            friendService.acceptFriendRequest(friendshipId, user.getId());
            return ResponseEntity.ok("Đã chấp nhận");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // API: Hủy kết bạn (Dùng cho Chat box hoặc nút Hủy bằng JS)
    @PostMapping("/api/friends/unfriend/{friendId}")
    @ResponseBody
    public ResponseEntity<?> unfriendApi(@PathVariable Long friendId, Principal principal) {
        User user = userRepository.findByUsername(principal.getName()).orElseThrow();
        friendService.unfriend(user.getId(), friendId);
        return ResponseEntity.ok("Đã hủy kết bạn");
    }

    // API: Lấy danh sách bạn bè (Dùng cho Chat load danh bạ)
    @GetMapping("/api/friends/list")
    @ResponseBody
    public List<ChatDTOs.UserDto> getFriendListApi(Principal principal) {
        User user = userRepository.findByUsername(principal.getName()).orElseThrow();
        List<User> friends = friendService.getFriendList(user.getId());
        return friends.stream().map(ChatDTOs.UserDto::new).collect(Collectors.toList());
    }

    // API: Tìm kiếm bạn bè (Dùng cho Tab Search trong friends.html)
    @GetMapping("/api/friends/search")
    @ResponseBody
    public List<ChatDTOs.UserDto> searchUsers(@RequestParam("q") String keyword, Principal principal) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        User currentUser = userRepository.findByUsername(principal.getName()).orElseThrow();
        List<User> users = userRepository.searchUsers(keyword.trim(), currentUser.getId());
        return users.stream().map(ChatDTOs.UserDto::new).collect(Collectors.toList());
    }
}