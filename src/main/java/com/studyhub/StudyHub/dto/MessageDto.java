package com.studyhub.StudyHub.dto;


import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MessageDto {
    private Long id;
    private String content;
    private LocalDateTime timestamp;
    private Long senderId;
    private String senderName;
    private Long recipientId;
    private String recipientName;
}