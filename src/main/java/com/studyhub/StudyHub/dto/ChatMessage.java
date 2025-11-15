package com.studyhub.StudyHub.dto;

import lombok.Data;

@Data // Lombok: Tự tạo Getter, Setter...
public class ChatMessage {
    private String content; // Nội dung tin nhắn
    private String senderUsername; // Tên người gửi
    private Long recipientId; // ID của người nhận (dùng cho chat 1-1)
}