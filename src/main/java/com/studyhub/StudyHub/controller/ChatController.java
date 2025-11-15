package com.studyhub.StudyHub.controller;


import com.studyhub.StudyHub.dto.ChatDTOs;
import com.studyhub.StudyHub.entity.ChatRoom;
import com.studyhub.StudyHub.entity.Message;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.ChatRoomRepository;
import com.studyhub.StudyHub.repository.MessageRepository;
import com.studyhub.StudyHub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;

@Controller
public class ChatController {

    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private MessageRepository messageRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ChatRoomRepository chatRoomRepository;

    /**
     * Xử lý gửi tin nhắn (Client -> Server)
     */
    @MessageMapping("/chat.sendMessage")
    @Transactional // Dùng Transactional để lấy sender và room
    public void sendMessage(@Payload ChatDTOs.SendMessageDto dto, Principal principal) {

        // === SỬA LỖI Ở ĐÂY ===
        // 1. Lấy email (principal.getName())
        String usernameOrEmail = principal.getName();
        // 2. Tìm bằng findByUsernameOrEmail
        User sender = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + usernameOrEmail));
        // === KẾT THÚC SỬA LỖI ===

        // 3. Tìm phòng chat
        ChatRoom room = chatRoomRepository.findById(dto.getRoomId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng: " + dto.getRoomId()));

        // 4. Tạo và lưu tin nhắn
        Message message = new Message();
        message.setSender(sender);
        message.setRoom(room);
        message.setContent(dto.getContent());

        Message savedMessage = messageRepository.save(message);

        // 5. Tạo DTO trả về (đầy đủ)
        ChatDTOs.MessageDto messageDto = new ChatDTOs.MessageDto(savedMessage);

        // 6. Gửi tin nhắn đến TẤT CẢ MỌI NGƯỜI trong phòng
        messagingTemplate.convertAndSend("/topic/room/" + room.getId(), messageDto);
    }

    /**
     * Xử lý sự kiện "đang gõ"
     */
    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload ChatDTOs.TypingDto dto, Principal principal) {
        // Gán username (chứ không phải email) của người gõ
        // (Lưu ý: Nếu principal là email, chúng ta cần tìm username)
        User user = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();
        dto.setUsername(user.getUsername()); // Gửi username thật

        messagingTemplate.convertAndSend("/topic/room/" + dto.getRoomId() + "/typing", dto);
    }
}