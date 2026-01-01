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

    // --- VIEW: Trang quản lý bạn bè ---
    @GetMapping("/friends")
    public String friendsPage(Model model, Principal principal) {
        User user = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();
        List<Friendship> requests = friendService.getPendingRequests(user.getId());

        model.addAttribute("requests", requests);
        model.addAttribute("currentUserId", user.getId());
        return "friends"; // file friends.html
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
}