package com.studyhub.StudyHub.service;



import com.studyhub.StudyHub.dto.ChatDTOs.ChatRoomDto;
import com.studyhub.StudyHub.dto.ChatDTOs.MessageDto;
import com.studyhub.StudyHub.entity.User;

import java.util.List;

public interface ChatService {
    // Lấy tất cả phòng chat của 1 user
    List<ChatRoomDto> getChatRooms(User currentUser);

    // Lấy hoặc tạo phòng 1-1
    ChatRoomDto getOrCreateOneToOneRoom(User user1, User user2);

    // Lấy lịch sử tin nhắn của 1 phòng
    List<MessageDto> getMessageHistory(Long roomId);
    // === THÊM DÒNG NÀY ===
    ChatRoomDto createGroupRoom(String groupName, java.util.List<Long> memberIds, User creator);
}