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
     * Xử lý gửi tin nhắn (TEXT, IMAGE, FILE)
     */
    @MessageMapping("/chat.sendMessage")
    @Transactional
    public void sendMessage(@Payload ChatDTOs.SendMessageDto dto, Principal principal) {

        String usernameOrEmail = principal.getName();
        User sender = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + usernameOrEmail));

        ChatRoom room = chatRoomRepository.findById(dto.getRoomId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng: " + dto.getRoomId()));

        // Tạo và lưu tin nhắn
        Message message = new Message();
        message.setSender(sender);
        message.setRoom(room);
        message.setContent(dto.getContent());

        // === XỬ LÝ FILE/IMAGE ===
        if (dto.getType() != null) {
            message.setType(dto.getType());
        } else {
            message.setType(Message.MessageType.TEXT);
        }

        message.setFilePath(dto.getFilePath());
        message.setFileName(dto.getFileName());
        message.setFileSize(dto.getFileSize());
        message.setMimeType(dto.getMimeType());

        Message savedMessage = messageRepository.save(message);

        // Tạo DTO trả về
        ChatDTOs.MessageDto messageDto = new ChatDTOs.MessageDto(savedMessage);

        // Gửi tin nhắn đến tất cả mọi người trong phòng
        messagingTemplate.convertAndSend("/topic/room/" + room.getId(), messageDto);
    }

    /**
     * Xử lý thu hồi tin nhắn
     */
    @MessageMapping("/chat.recallMessage")
    @Transactional
    public void recallMessage(@Payload ChatDTOs.RecallMessageDto dto, Principal principal) {

        String usernameOrEmail = principal.getName();
        User currentUser = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

        Message message = messageRepository.findById(dto.getMessageId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin nhắn"));

        // Kiểm tra quyền thu hồi (chỉ người gửi mới được thu hồi)
        if (!message.getSender().getId().equals(currentUser.getId())) {
            throw new RuntimeException("Bạn không có quyền thu hồi tin nhắn này");
        }

        // Đánh dấu tin nhắn đã thu hồi
        message.setRecalled(true);
        message.setContent("Tin nhắn đã được thu hồi");
        messageRepository.save(message);

        // Gửi thông báo thu hồi đến tất cả mọi người
        ChatDTOs.MessageDto messageDto = new ChatDTOs.MessageDto(message);
        messagingTemplate.convertAndSend("/topic/room/" + dto.getRoomId(), messageDto);
    }

    /**
     * Xử lý sự kiện "đang gõ"
     */
    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload ChatDTOs.TypingDto dto, Principal principal) {
        User user = userRepository.findByUsernameOrEmail(principal.getName(), principal.getName()).orElseThrow();
        dto.setUsername(user.getUsername());
        messagingTemplate.convertAndSend("/topic/room/" + dto.getRoomId() + "/typing", dto);
    }
}