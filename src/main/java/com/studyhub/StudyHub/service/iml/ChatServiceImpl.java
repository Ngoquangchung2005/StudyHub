package com.studyhub.StudyHub.service.iml;

import com.studyhub.StudyHub.dto.ChatDTOs;
import com.studyhub.StudyHub.dto.ChatDTOs.ChatRoomDto;
import com.studyhub.StudyHub.dto.ChatDTOs.MessageDto;
import com.studyhub.StudyHub.entity.ChatRoom;
import com.studyhub.StudyHub.entity.Friendship;
import com.studyhub.StudyHub.entity.Message;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.ChatRoomRepository;
import com.studyhub.StudyHub.repository.FriendshipRepository;
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

    // === THÊM: Inject Repository bạn bè để kiểm tra quan hệ ===
    @Autowired private FriendshipRepository friendshipRepository;

    @Autowired private SimpMessagingTemplate messagingTemplate;

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
        // === LOGIC MỚI: CHẶN NGƯỜI LẠ NHẮN TIN ===
        // Nếu 2 người khác nhau, kiểm tra xem có phải bạn bè không
        if (!user1.getId().equals(user2.getId())) {
            boolean isFriend = friendshipRepository.findRelationship(user1, user2)
                    .map(f -> f.getStatus() == Friendship.FriendshipStatus.ACCEPTED)
                    .orElse(false);

            if (!isFriend) {
                // Ném lỗi RuntimeException, Controller sẽ bắt lỗi này và trả về 400 hoặc 500
                throw new RuntimeException("Bạn phải kết bạn mới có thể nhắn tin!");
            }
        }
        // ============================================

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

        // 4. Gửi thông báo Realtime cho các thành viên
        for (User member : members) {
            if (!member.getId().equals(creator.getId())) {
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
    @Transactional(readOnly = true)
    public List<ChatDTOs.UserDto> getGroupMembers(Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Phòng không tồn tại"));
        return room.getMembers().stream()
                .map(ChatDTOs.UserDto::new)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addMemberToGroup(Long roomId, Long userId, User adder) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Phòng không tồn tại"));

        boolean isAdderInGroup = room.getMembers().stream().anyMatch(m -> m.getId().equals(adder.getId()));
        if (!isAdderInGroup) throw new RuntimeException("Bạn không phải thành viên nhóm này");

        User newMember = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        if (room.getMembers().add(newMember)) {
            chatRoomRepository.save(room);
            sendSystemMessage(room, adder.getName() + " đã thêm " + newMember.getName() + " vào nhóm.");
        }
    }

    @Override
    @Transactional
    public void removeMemberFromGroup(Long roomId, Long userId, User remover) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Phòng không tồn tại"));

        boolean isRemoverInGroup = room.getMembers().stream().anyMatch(m -> m.getId().equals(remover.getId()));
        if (!isRemoverInGroup) throw new RuntimeException("Bạn không phải thành viên nhóm này");

        User memberToRemove = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        if (room.getMembers().remove(memberToRemove)) {
            chatRoomRepository.save(room);
            sendSystemMessage(room, remover.getName() + " đã mời " + memberToRemove.getName() + " ra khỏi nhóm.");
        }
    }

    private void sendSystemMessage(ChatRoom room, String text) {
        Message systemMsg = new Message();
        systemMsg.setContent(text);
        systemMsg.setRoom(room);
        systemMsg.setSender(room.getMembers().iterator().next()); // Lấy tạm 1 người làm sender để tránh null
        systemMsg.setType(Message.MessageType.TEXT);
        systemMsg.setTimestamp(LocalDateTime.now());

        Message savedMsg = messageRepository.save(systemMsg);
        ChatDTOs.MessageDto msgDto = new ChatDTOs.MessageDto(savedMsg);
        messagingTemplate.convertAndSend("/topic/room/" + room.getId(), msgDto);
    }

    @Override
    @Transactional
    public void leaveGroup(Long roomId, User user) {
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