package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.dto.ChatDTOs;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.ChatService;
import com.studyhub.StudyHub.service.PresenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping; // <-- Thêm import
import org.springframework.web.bind.annotation.RequestBody; // <-- Thêm import

import java.security.Principal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
public class ChatRoomController {

    @Autowired private ChatService chatService;
    @Autowired private UserRepository userRepository;
    // === THÊM DÒNG NÀY ===
    @Autowired private PresenceService presenceService;

    // Helper
    private User getCurrentUser(Principal principal) {
        // Lấy email (hoặc username) từ principal
        String usernameOrEmail = principal.getName();

        // SỬA LỖI: Tìm bằng findByUsernameOrEmail
        return userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user đã đăng nhập: " + usernameOrEmail));
    }

    // API 1: Lấy tất cả phòng chat của user
    @GetMapping("/rooms")
    public ResponseEntity<List<ChatDTOs.ChatRoomDto>> getMyChatRooms(Principal principal) {
        User currentUser = getCurrentUser(principal);
        return ResponseEntity.ok(chatService.getChatRooms(currentUser));
    }

    // API 2: Lấy tất cả user (để bắt đầu chat)
    @GetMapping("/users")
    public ResponseEntity<List<ChatDTOs.UserDto>> getAllUsers(Principal principal) {
        User currentUser = getCurrentUser(principal);
        List<ChatDTOs.UserDto> users = userRepository.findAll().stream()
                .filter(user -> !user.getId().equals(currentUser.getId())) // Lọc chính mình
                .map(ChatDTOs.UserDto::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    // API 3: Lấy/Tạo phòng 1-1
    @GetMapping("/room/with/{otherUserId}")
    public ResponseEntity<ChatDTOs.ChatRoomDto> getOneToOneRoom(
            @PathVariable Long otherUserId, Principal principal) {
        User currentUser = getCurrentUser(principal);
        User otherUser = userRepository.findById(otherUserId).orElseThrow();
        return ResponseEntity.ok(chatService.getOrCreateOneToOneRoom(currentUser, otherUser));
    }

    // API 4: Lấy lịch sử tin nhắn của 1 phòng
    @GetMapping("/room/{roomId}/messages")
    public ResponseEntity<List<ChatDTOs.MessageDto>> getMessageHistory(
            @PathVariable Long roomId, Principal principal) {
        // (Cần thêm logic bảo mật để check user có ở trong phòng này không)
        return ResponseEntity.ok(chatService.getMessageHistory(roomId));
    }
    // API 5: Lấy danh sách tất cả user đang online
    @GetMapping("/online-users")
    public ResponseEntity<Set<String>> getOnlineUsers() {
        return ResponseEntity.ok(presenceService.getOnlineUsers());
    }
    // === THÊM API MỚI ===
    @PostMapping("/room/group")
    public ResponseEntity<ChatDTOs.ChatRoomDto> createGroupRoom(
            @RequestBody ChatDTOs.CreateGroupRequest request,
            Principal principal) {

        User currentUser = getCurrentUser(principal);

        // Validate cơ bản
        if (request.getGroupName() == null || request.getGroupName().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ChatDTOs.ChatRoomDto newRoom = chatService.createGroupRoom(
                request.getGroupName(),
                request.getMemberIds(),
                currentUser
        );

        return ResponseEntity.ok(newRoom);
    }
}