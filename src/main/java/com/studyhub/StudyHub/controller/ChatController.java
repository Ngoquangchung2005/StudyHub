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

        // === THÊM: GỬI SỰ KIỆN RIÊNG TỚI TỪNG USER ĐỂ HIỆN "CHẤM ĐỎ" Ở CÁC PHÒNG KHÁC ===
        // Lý do: Client chỉ subscribe topic của phòng đang mở, nên sẽ KHÔNG nhận được message
        // ở các phòng khác => không thể hiện dấu chấm đỏ.
        // Gửi thêm qua /user/queue/chat để client biết có tin nhắn mới ở roomId nào.
        for (User member : room.getMembers()) {
            if (member.getId().equals(sender.getId())) continue;
            // Security/principal hiện định danh bằng Email -> phải dùng Email để định tuyến.
            if (member.getEmail() == null) continue;
            messagingTemplate.convertAndSendToUser(
                    member.getEmail(),
                    "/queue/chat",
                    messageDto
            );
        }

        System.out.println("✅ Đã gửi tin nhắn mới qua WebSocket - ID: " + savedMessage.getId());
    }

    /**
     * Xử lý thu hồi tin nhắn - QUAN TRỌNG: PHẢI BROADCAST ĐẾN TẤT CẢ NGƯỜI DÙNG
     */
    @MessageMapping("/chat.recallMessage")
    @Transactional
    public void recallMessage(@Payload ChatDTOs.RecallMessageDto dto, Principal principal) {

        System.out.println("🔔 Server nhận lệnh thu hồi tin nhắn ID: " + dto.getMessageId());

        String usernameOrEmail = principal.getName();
        User currentUser = userRepository.findByUsernameOrEmail(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user"));

        Message message = messageRepository.findById(dto.getMessageId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin nhắn"));

        // Kiểm tra quyền thu hồi (chỉ người gửi mới được thu hồi)
        if (!message.getSender().getId().equals(currentUser.getId())) {
            System.out.println("❌ Người dùng không có quyền thu hồi tin nhắn này");
            throw new RuntimeException("Bạn không có quyền thu hồi tin nhắn này");
        }

        // Đánh dấu tin nhắn đã thu hồi
        message.setRecalled(true);
        message.setContent("Tin nhắn đã được thu hồi");
        Message updatedMessage = messageRepository.save(message);

        System.out.println("💾 Đã lưu tin nhắn thu hồi vào database");

        // === QUAN TRỌNG: GỬI TIN NHẮN ĐÃ THU HỒI ĐẾN TẤT CẢ NGƯỜI TRONG PHÒNG ===
        ChatDTOs.MessageDto messageDto = new ChatDTOs.MessageDto(updatedMessage);

        String topic = "/topic/room/" + dto.getRoomId();
        messagingTemplate.convertAndSend(topic, messageDto);

        System.out.println("📤 Đã broadcast tin nhắn thu hồi đến: " + topic);
        System.out.println("   - Message ID: " + messageDto.getId());
        System.out.println("   - isRecalled: " + messageDto.isRecalled());
        System.out.println("   - Content: " + messageDto.getContent());
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
    // ... các import giữ nguyên

    // === SỬA LẠI HÀM NÀY ===
    @MessageMapping("/chat.videoCall")
    public void handleVideoCallSignal(@Payload ChatDTOs.WebRTCMessage message, Principal principal) {

        // 1. XỬ LÝ NGƯỜI GỬI (SENDER)
        // principal.getName() hiện tại là Email (do CustomUserDetailsService quy định)
        // Nhưng Client cần Username để hiển thị ai đang gọi.
        User senderUser = userRepository.findByEmail(principal.getName()).orElse(null);
        if (senderUser != null) {
            message.setSender(senderUser.getUsername()); // Gửi Username (VD: "chung")
        } else {
            message.setSender(principal.getName()); // Fallback
        }

        System.out.println("📹 Video Signal [" + message.getType() + "] from " + message.getSender() + " to " + message.getRecipient());

        // 2. XỬ LÝ NGƯỜI NHẬN (RECIPIENT)
        // Client gửi lên Username (VD: "azin"), nhưng WebSocket cần Email (VD: "azin@gmail.com") để định tuyến.
        String recipientUsername = message.getRecipient();

        User recipientUser = userRepository.findByUsername(recipientUsername)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user có username: " + recipientUsername));

        // 3. GỬI TÍN HIỆU
        // Dùng Email của người nhận để gửi tin nhắn
        messagingTemplate.convertAndSendToUser(
                recipientUser.getEmail(), // <--- QUAN TRỌNG: Phải dùng Email
                "/queue/video-call",
                message
        );
    }
}