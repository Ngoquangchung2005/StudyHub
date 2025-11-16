package com.studyhub.StudyHub.config;

import com.studyhub.StudyHub.dto.ChatDTOs.PresenceDto;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.PresenceService; // <-- THÊM IMPORT
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class WebSocketEventListener {

    @Autowired private SimpMessageSendingOperations messagingTemplate;
    @Autowired private UserRepository userRepository;

    // === THÊM DÒNG NÀY ===
    @Autowired private PresenceService presenceService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        Principal userPrincipal = event.getUser();
        if(userPrincipal != null) {
            String email = userPrincipal.getName();
            User user = userRepository.findByUsernameOrEmail(email, email)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy user từ principal"));

            String username = user.getUsername();

            // === THÊM DÒNG NÀY ===
            // 1. Thêm user vào "bộ nhớ"
            presenceService.userConnected(username);

            // 2. Gửi thông báo (như cũ)
            PresenceDto presenceMsg = new PresenceDto();
            presenceMsg.setUsername(username);
            presenceMsg.setStatus("ONLINE");

            messagingTemplate.convertAndSend("/topic/presence", presenceMsg);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        Principal userPrincipal = event.getUser();
        if(userPrincipal != null) {
            String email = userPrincipal.getName();
            User user = userRepository.findByUsernameOrEmail(email, email)
                    .orElse(null);

            if (user != null) {
                String username = user.getUsername();

                // === THÊM DÒNG NÀY ===
                // 1. Xóa user khỏi "bộ nhớ"
                presenceService.userDisconnected(username);

                // 2. Gửi thông báo (như cũ)
                PresenceDto presenceMsg = new PresenceDto();
                presenceMsg.setUsername(username);
                presenceMsg.setStatus("OFFLINE");

                messagingTemplate.convertAndSend("/topic/presence", presenceMsg);
            }
        }
    }
}