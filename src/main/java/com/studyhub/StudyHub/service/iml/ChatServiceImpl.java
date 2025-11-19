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
import com.studyhub.StudyHub.repository.UserRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    @Autowired private ChatRoomRepository chatRoomRepository;
    @Autowired private MessageRepository messageRepository;
    // === THÊM DÒNG NÀY ===
    @Autowired private UserRepository userRepository;

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
    // === THÊM HÀM IMPLEMENT MỚI ===
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

        ChatRoom savedRoom = chatRoomRepository.save(room);
        return new ChatRoomDto(savedRoom, creator);
    }
    // === THÊM HÀM IMPLEMENT NÀY ===
    @Override
    @Transactional
    public void leaveGroup(Long roomId, User user) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng chat"));

        // Chỉ cho phép rời nếu là chat NHÓM
        if (room.getType() != ChatRoom.RoomType.GROUP) {
            throw new RuntimeException("Không thể rời khỏi cuộc trò chuyện 1-1. Hãy xóa bạn bè nếu muốn.");
        }

        // Xóa user khỏi danh sách thành viên
        // Lưu ý: Set.remove() dựa vào equals() và hashCode() của User.
        // Để chắc chắn, ta dùng removeIf dựa trên ID
        room.getMembers().removeIf(member -> member.getId().equals(user.getId()));

        // (Tùy chọn) Nếu nhóm không còn ai thì xóa luôn nhóm
        if (room.getMembers().isEmpty()) {
            chatRoomRepository.delete(room);
        } else {
            chatRoomRepository.save(room);

            // (Tùy chọn nâng cao) Gửi tin nhắn hệ thống thông báo user đã rời nhóm
            // Bạn có thể thêm logic gửi tin nhắn vào đây nếu muốn
        }
    }

}
