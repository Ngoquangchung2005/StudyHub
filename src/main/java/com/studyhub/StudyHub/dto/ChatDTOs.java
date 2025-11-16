package com.studyhub.StudyHub.dto;



import com.studyhub.StudyHub.entity.ChatRoom;
import com.studyhub.StudyHub.entity.User;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

// Dùng 1 file để chứa các DTOs con
public class ChatDTOs {

    // Thông tin 1 User cơ bản
    @Data
    public static class UserDto {
        private Long id;
        private String name;
        private String username;

        public UserDto(User user) {
            this.id = user.getId();
            this.name = user.getName();
            this.username = user.getUsername();
        }
    }

    // Thông tin 1 Phòng chat (cho sidebar)
    @Data
    public static class ChatRoomDto {
        private Long id;
        private String name;
        private ChatRoom.RoomType type;
        private Set<UserDto> members;
        // Dùng cho chat 1-1, để hiển thị tên "người kia"
        private String oneToOnePartnerName;

        public ChatRoomDto(ChatRoom room, User currentUser) {
            this.id = room.getId();
            this.name = room.getName();
            this.type = room.getType();
            this.members = room.getMembers().stream()
                    .map(UserDto::new)
                    .collect(Collectors.toSet());

            // Tìm tên "người kia"
            if (this.type == ChatRoom.RoomType.ONE_TO_ONE) {
                this.oneToOnePartnerName = room.getMembers().stream()
                        .filter(member -> !member.getId().equals(currentUser.getId()))
                        .findFirst()
                        .map(User::getName)
                        .orElse("Chat");
            }
        }
    }

    // Gói tin khi Client GỬI tin nhắn
    @Data
    public static class SendMessageDto {
        private Long roomId;
        private String content;
    }

    // Gói tin khi Server TRẢ VỀ tin nhắn (cho lịch sử và real-time)
    @Data
    public static class MessageDto {
        private Long id;
        private String content;
        private LocalDateTime timestamp;
        private Long senderId;
        private String senderName;
        private Long roomId;

        public MessageDto(com.studyhub.StudyHub.entity.Message msg) {
            this.id = msg.getId();
            this.content = msg.getContent();
            this.timestamp = msg.getTimestamp();
            this.senderId = msg.getSender().getId();
            this.senderName = msg.getSender().getName();
            this.roomId = msg.getRoom().getId();
        }
    }

    // Gói tin cho sự kiện "Đang gõ"
    @Data
    public static class TypingDto {
        private Long roomId;
        private String username;
        private boolean isTyping;
    }

    // Gói tin cho sự kiện Online/Offline
    @Data
    public static class PresenceDto {
        private String username;
        private String status; // "ONLINE" hoặc "OFFLINE"
    }
}