package com.studyhub.StudyHub.controller;

import com.studyhub.StudyHub.dto.NotificationDto; // <-- Dùng DTO
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired private NotificationService notificationService;
    @Autowired private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<List<NotificationDto>> getMyNotifications(Principal principal) {
        User user = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();
        return ResponseEntity.ok(notificationService.getUserNotifications(user.getId()));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(Principal principal) {
        User user = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();
        return ResponseEntity.ok(notificationService.countUnread(user.getId()));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Principal principal) {
        User user = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }
    // === THÊM API NÀY: Xóa tất cả thông báo ===
    @DeleteMapping("/delete-all")
    public ResponseEntity<Void> deleteAllNotifications(Principal principal) {
        User user = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();
        notificationService.deleteAllNotifications(user.getId());
        return ResponseEntity.ok().build();
    }
}