package com.studyhub.StudyHub.dto;

import com.studyhub.StudyHub.entity.Comment;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.format.DateTimeFormatter;

@Data
@NoArgsConstructor
public class CommentDto {
    // Dùng để nhận dữ liệu từ Form
    private String content;

    // === THÊM CÁC TRƯỜNG ĐỂ TRẢ VỀ REALTIME ===
    private Long id;
    private Long postId;
    private String senderName;
    private String senderAvatar;
    private String senderUsername;
    private String createdAt;

    // Constructor chuyển từ Entity sang DTO
    public CommentDto(Comment comment) {
        this.id = comment.getId();
        this.content = comment.getContent();
        this.postId = comment.getPost().getId();
        this.senderName = comment.getUser().getName();
        this.senderUsername = comment.getUser().getUsername();
        this.senderAvatar = comment.getUser().getAvatarUrl();

        // Format ngày giờ đẹp để hiển thị (VD: 10:30 20/11/2024)
        if (comment.getCreatedAt() != null) {
            this.createdAt = comment.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
        }
    }
}