package com.studyhub.StudyHub.service.iml;

import com.studyhub.StudyHub.dto.ChatDTOs;
import com.studyhub.StudyHub.dto.ChatDTOs.ChatRoomDto;
import com.studyhub.StudyHub.dto.ChatDTOs.MessageDto;
import com.studyhub.StudyHub.entity.ChatRoom;
import com.studyhub.StudyHub.entity.Message;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.ChatRoomRepository;
import com.studyhub.StudyHub.repository.MessageRepository;
import com.studyhub.StudyHub.repository.UserRepository;
import com.studyhub.StudyHub.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private UserRepository userRepository;

    // Inject SimpMessagingTemplate để gửi socket
    @Autowired private SimpMessagingTemplate messagingTemplate;

    // ... (Giữ nguyên các hàm getChatRooms, getOrCreateOneToOneRoom, getMessageHistory...)

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
        ChatRoom room = chatRoomRepository.findOneToOneRoom(user1, user2)
                .orElseGet(() -> {
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
        return messageRepository.findByRoomIdWithSender(roomId, Sort.by(Sort.Direction.ASC, "timestamp"))
                .stream()
                .map(MessageDto::new)
                .collect(Collectors.toList());
    }

    // === SỬA HÀM NÀY ===
    @Override
    @Transactional
    public ChatRoomDto createGroupRoom(String groupName, List<Long> memberIds, User creator) {
        ChatRoom room = new ChatRoom();
        room.setName(groupName);
        room.setType(ChatRoom.RoomType.GROUP);

        // 1. Tìm các thành viên từ list ID
        Set<User> members = new HashSet<>();
        if (memberIds != null && !memberIds.isEmpty()) {
            List<User> users = userRepository.findAllById(memberIds);
            members.addAll(users);
        }

        // 2. Luôn thêm người tạo vào nhóm
        members.add(creator);
        room.setMembers(members);

        // 3. Lưu nhóm vào Database
        ChatRoom savedRoom = chatRoomRepository.save(room);

        // 4. === QUAN TRỌNG: Gửi thông báo Realtime cho các thành viên ===
        // Lặp qua tất cả thành viên để báo "Có phòng mới"
        for (User member : members) {
            // Không cần gửi cho người tạo (vì Frontend của người tạo đã tự xử lý sau khi API trả về)
            // Nhưng gửi cũng không sao, để chắc chắn thì ta gửi cho những người KHÁC người tạo
            if (!member.getId().equals(creator.getId())) {
                // Gửi tín hiệu chuỗi "NEW_ROOM" vào queue riêng của user đó
                // Client JS sẽ nhận được ở hàm onNotificationReceived
                messagingTemplate.convertAndSendToUser(
                        member.getUsername(),
                        "/queue/notify",
                        "NEW_ROOM"
                );
            }
        }

        return new ChatRoomDto(savedRoom, creator);
    }

    @Override
    @Transactional
    public void leaveGroup(Long roomId, User user) {
        // ... (Giữ nguyên logic rời nhóm đã viết ở câu trả lời trước) ...
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng chat"));

        if (room.getType() != ChatRoom.RoomType.GROUP) {
            throw new RuntimeException("Không thể rời khỏi cuộc trò chuyện 1-1.");
        }

        boolean removed = room.getMembers().removeIf(member -> member.getId().equals(user.getId()));
        if (!removed) throw new RuntimeException("Bạn không phải là thành viên nhóm này.");

        if (room.getMembers().isEmpty()) {
            chatRoomRepository.delete(room);
        } else {
            chatRoomRepository.save(room);

            Message systemMsg = new Message();
            systemMsg.setContent(user.getName() + " đã rời khỏi nhóm.");
            systemMsg.setRoom(room);
            systemMsg.setSender(user);
            systemMsg.setType(Message.MessageType.TEXT);
            systemMsg.setTimestamp(LocalDateTime.now());
            Message savedMsg = messageRepository.save(systemMsg);

            ChatDTOs.MessageDto msgDto = new ChatDTOs.MessageDto(savedMsg);
            messagingTemplate.convertAndSend("/topic/room/" + roomId, msgDto);
        }
    }
}