package com.studyhub.StudyHub.controller;



import com.studyhub.StudyHub.dto.ChatMessage;
import com.studyhub.StudyHub.entity.Message;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.MessageRepository;
import com.studyhub.StudyHub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

@Controller
public class ChatController {

    @Autowired
    private SimpMessagingTemplate simpMessagingTemplate; // Dùng để gửi tin nhắn

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private UserRepository userRepository;

    // --- HÀM XỬ LÝ CHAT 1-1 ---

    // "Bắt" các tin nhắn được gửi đến địa chỉ "/app/chat.sendMessage"
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage) {

        // 1. Tìm người gửi và người nhận trong CSDL
        User sender = userRepository.findByUsername(chatMessage.getSenderUsername())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người gửi"));

        User recipient = userRepository.findById(chatMessage.getRecipientId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người nhận"));

        // 2. Tạo đối tượng Message (Entity) để lưu vào CSDL
        Message message = new Message();
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(chatMessage.getContent());
        message.setTimestamp(LocalDateTime.now());

        // 3. Lưu tin nhắn vào CSDL
        messageRepository.save(message);

        // 4. Gửi tin nhắn đến "hàng đợi" (queue) CÁ NHÂN của người nhận
        // Chỉ người nhận (recipient) mới nhận được tin nhắn này
        // Ví dụ: Gửi đến /queue/user-123-messages
        simpMessagingTemplate.convertAndSendToUser(
                recipient.getUsername(), // Tên của người nhận
                "/queue/messages",       // Endpoint cá nhân
                chatMessage              // Nội dung tin nhắn
        );
    }
}