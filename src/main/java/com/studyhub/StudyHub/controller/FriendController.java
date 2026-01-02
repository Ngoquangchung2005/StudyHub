package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.entity.Friendship;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.service.FriendService;
import com.studyhub.StudyHub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

@Controller
public class FriendController {

    @Autowired private FriendService friendService;
    @Autowired private UserRepository userRepository;

    @GetMapping("/friends")
    public String friendsPage(Model model, Principal principal) {
        User user = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();

        // 1. Lấy danh sách lời mời
        List<Friendship> requests = friendService.getPendingRequests(user.getId());

        // 2. Lấy danh sách bạn bè hiện tại (MỚI THÊM)
        List<User> friends = friendService.getFriendList(user.getId());

        model.addAttribute("requests", requests);
        model.addAttribute("friends", friends); // Truyền sang view
        model.addAttribute("currentUserId", user.getId());

        return "friends";
    }

    // --- API: Lấy danh sách bạn bè (cho Chat Sidebar) ---
    @GetMapping("/api/friends/list")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getMyFriends(Principal principal) {
        User user = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();
        List<User> friends = friendService.getFriendList(user.getId());

        // Convert sang map đơn giản để trả về JSON
        List<Map<String, Object>> response = friends.stream().map(f -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", f.getId());
            map.put("username", f.getUsername());
            map.put("name", f.getName());
            map.put("avatarUrl", f.getAvatarUrl());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // --- API: Tìm kiếm bạn bè (MỚI THÊM) ---
    @GetMapping("/api/friends/search")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> searchUsers(@RequestParam("keyword") String keyword, Principal principal) {
        User currentUser = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();

        // 1. Tìm kiếm user (Gọi service hoặc repo)
        // Lưu ý: Đảm bảo bạn đã thêm hàm searchUsers vào UserRepository như hướng dẫn trước
        List<User> users = userRepository.searchUsers(keyword, currentUser.getId());

        // 2. Map sang JSON và kiểm tra trạng thái bạn bè
        List<Map<String, Object>> response = users.stream().map(u -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", u.getId());
            map.put("name", u.getName());
            map.put("username", u.getUsername());
            map.put("avatarUrl", u.getAvatarUrl());

            // Kiểm tra trạng thái: NONE, SENT, RECEIVED, FRIEND
            String status = friendService.getFriendshipStatus(currentUser.getId(), u.getId());
            map.put("status", status);

            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // --- API: Gửi lời mời ---
    @PostMapping("/api/friends/request/{userId}")
    @ResponseBody
    public ResponseEntity<?> sendRequest(@PathVariable Long userId, Principal principal) {
        User user = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();
        friendService.sendFriendRequest(user.getId(), userId);
        return ResponseEntity.ok("Đã gửi lời mời");
    }

    // --- API: Chấp nhận lời mời ---
    @PostMapping("/api/friends/accept/{friendshipId}")
    @ResponseBody
    public ResponseEntity<?> acceptRequest(@PathVariable Long friendshipId, Principal principal) {
        User user = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();
        friendService.acceptFriendRequest(friendshipId, user.getId());
        return ResponseEntity.ok("Đã chấp nhận");
    }

    // --- API: Hủy kết bạn ---
    @PostMapping("/api/friends/unfriend/{friendId}")
    @ResponseBody
    public ResponseEntity<?> unfriendUser(@PathVariable Long friendId, Principal principal) {
        User user = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();

        // Gọi service xử lý logic unfriend
        friendService.unfriend(user.getId(), friendId);

        return ResponseEntity.ok("Đã hủy kết bạn");
    }
}