package com.studyhub.StudyHub.dto;

import com.studyhub.StudyHub.entity.ChatRoom;
import com.studyhub.StudyHub.entity.Message;
import com.studyhub.StudyHub.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

public class ChatDTOs {

    @Data
    public static class UserDto {
        private Long id;
        private String name;
        private String username;
        private String avatarUrl;

        public UserDto(User user) {
            this.id = user.getId();
            this.name = user.getName();
            this.username = user.getUsername();
            this.avatarUrl = user.getAvatarUrl();
        }
    }

    @Data
    public static class ChatRoomDto {
        private Long id;
        private String name;
        private ChatRoom.RoomType type;
        private Set<UserDto> members;
        private String oneToOnePartnerName;
        private Long oneToOnePartnerId;
        private String oneToOnePartnerUsername;
        private String oneToOnePartnerAvatarUrl;

        public ChatRoomDto(ChatRoom room, User currentUser) {
            this.id = room.getId();
            this.name = room.getName();
            this.type = room.getType();
            this.members = room.getMembers().stream()
                    .map(UserDto::new)
                    .collect(Collectors.toSet());

            if (this.type == ChatRoom.RoomType.ONE_TO_ONE) {
                room.getMembers().stream()
                        .filter(member -> !member.getId().equals(currentUser.getId()))
                        .findFirst()
                        .ifPresent(partner -> {
                            this.oneToOnePartnerName = partner.getName();
                            this.oneToOnePartnerId = partner.getId();
                            this.oneToOnePartnerUsername = partner.getUsername();
                            this.oneToOnePartnerAvatarUrl = partner.getAvatarUrl();
                        });
                if (this.oneToOnePartnerName == null) {
                    this.oneToOnePartnerName = "Chat";
                }
            }
        }
    }

    @Data
    public static class SendMessageDto {
        private Long roomId;
        private String content;
        private Message.MessageType type;
        private String filePath;
        private String fileName;
        private Long fileSize;
        private String mimeType;
    }

    @Data
    public static class MessageDto {
        private Long id;
        private String content;
        private String timestamp;
        private Long senderId;
        private String senderName;
        private Long roomId;

        // ====== QUAN TRỌNG: Thêm annotation để JSON serialize đúng tên field ======
        @JsonProperty("isRecalled")  // ← Bắt buộc JSON dùng tên "isRecalled"
        private boolean isRecalled;

        private Message.MessageType type;
        private String filePath;
        private String fileName;
        private Long fileSize;
        private String mimeType;

        public MessageDto(Message msg) {
            this.id = msg.getId();
            this.content = msg.getContent();

            if (msg.getTimestamp() != null) {
                this.timestamp = msg.getTimestamp().toString();
            } else {
                this.timestamp = java.time.LocalDateTime.now().toString();
            }

            this.senderId = msg.getSender().getId();
            this.senderName = msg.getSender().getName();
            this.roomId = msg.getRoom().getId();
            this.isRecalled = msg.isRecalled();  // ← Getter vẫn dùng isRecalled()
            this.type = msg.getType();
            this.filePath = msg.getFilePath();
            this.fileName = msg.getFileName();
            this.fileSize = msg.getFileSize();
            this.mimeType = msg.getMimeType();
        }
    }

    @Data
    public static class TypingDto {
        private Long roomId;
        private String username;
        private boolean isTyping;
    }

    @Data
    public static class PresenceDto {
        private String username;
        private String status;
    }

    @Data
    public static class RecallMessageDto {
        private Long messageId;
        private Long roomId;
    }
}