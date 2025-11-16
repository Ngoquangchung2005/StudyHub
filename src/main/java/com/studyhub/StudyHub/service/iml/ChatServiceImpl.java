package com.studyhub.StudyHub.service.iml;



import com.studyhub.StudyHub.dto.ChatDTOs.ChatRoomDto;
import com.studyhub.StudyHub.dto.ChatDTOs.MessageDto;
import com.studyhub.StudyHub.entity.ChatRoom;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.ChatRoomRepository;
import com.studyhub.StudyHub.repository.MessageRepository;
import com.studyhub.StudyHub.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private MessageRepository messageRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomDto> getChatRooms(User currentUser) {
        return chatRoomRepository.findByMembersContains(currentUser)
                .stream()
                .map(room -> new ChatRoomDto(room, currentUser))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChatRoomDto getOrCreateOneToOneRoom(User user1, User user2) {
        // Thử tìm phòng 1-1 đã có
        ChatRoom room = chatRoomRepository.findOneToOneRoom(user1, user2)
                .orElseGet(() -> {
                    // Nếu không có, tạo phòng mới
                    ChatRoom newRoom = new ChatRoom();
                    newRoom.setType(ChatRoom.RoomType.ONE_TO_ONE);
                    newRoom.setMembers(Set.of(user1, user2));
                    return chatRoomRepository.save(newRoom);
                });
        return new ChatRoomDto(room, user1);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MessageDto> getMessageHistory(Long roomId) {
        // Dùng query đã tối ưu (JOIN FETCH sender)
        return messageRepository.findByRoomIdWithSender(roomId, Sort.by(Sort.Direction.ASC, "timestamp"))
                .stream()
                .map(MessageDto::new)
                .collect(Collectors.toList());
    }
}