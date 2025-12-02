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
    // ... các import và autowired cũ

    // === TRIỂN KHAI HÀM LẤY THÀNH VIÊN ===
    @Override
    @Transactional(readOnly = true)
    public List<ChatDTOs.UserDto> getGroupMembers(Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Phòng không tồn tại"));
        return room.getMembers().stream()
                .map(ChatDTOs.UserDto::new)
                .collect(Collectors.toList());
    }

    // === TRIỂN KHAI HÀM THÊM THÀNH VIÊN ===
    @Override
    @Transactional
    public void addMemberToGroup(Long roomId, Long userId, User adder) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Phòng không tồn tại"));

        // Kiểm tra quyền: Người thêm phải đang ở trong nhóm
        boolean isAdderInGroup = room.getMembers().stream().anyMatch(m -> m.getId().equals(adder.getId()));
        if (!isAdderInGroup) throw new RuntimeException("Bạn không phải thành viên nhóm này");

        User newMember = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        // Thêm vào nhóm
        if (room.getMembers().add(newMember)) { // Set trả về true nếu chưa có
            chatRoomRepository.save(room);

            // Gửi thông báo hệ thống vào nhóm
            sendSystemMessage(room, adder.getName() + " đã thêm " + newMember.getName() + " vào nhóm.");
        }
    }

    // === TRIỂN KHAI HÀM XÓA THÀNH VIÊN (KICK) ===
    @Override
    @Transactional
    public void removeMemberFromGroup(Long roomId, Long userId, User remover) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Phòng không tồn tại"));

        // Kiểm tra quyền (Tạm thời cho phép thành viên kick, thực tế nên check Admin)
        boolean isRemoverInGroup = room.getMembers().stream().anyMatch(m -> m.getId().equals(remover.getId()));
        if (!isRemoverInGroup) throw new RuntimeException("Bạn không phải thành viên nhóm này");

        User memberToRemove = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        // Xóa khỏi nhóm
        if (room.getMembers().remove(memberToRemove)) {
            chatRoomRepository.save(room);

            // Gửi thông báo hệ thống
            sendSystemMessage(room, remover.getName() + " đã mời " + memberToRemove.getName() + " ra khỏi nhóm.");
        }
    }

    // Hàm phụ trợ gửi tin nhắn hệ thống (Tái sử dụng code cũ hoặc viết mới)
    private void sendSystemMessage(ChatRoom room, String text) {
        Message systemMsg = new Message();
        systemMsg.setContent(text);
        systemMsg.setRoom(room);
        // Set sender là null hoặc 1 user hệ thống ảo, ở đây tạm lấy user đầu tiên để tránh lỗi null nếu DB bắt buộc
        // Hoặc tốt nhất sửa Entity Message để sender có thể null.
        // Ở đây mình dùng tạm logic lấy member đầu tiên làm người gửi tin hệ thống để tránh crash
        systemMsg.setSender(room.getMembers().iterator().next());
        systemMsg.setType(Message.MessageType.TEXT); // Hoặc tạo loại SYSTEM mới
        systemMsg.setTimestamp(LocalDateTime.now());

        Message savedMsg = messageRepository.save(systemMsg);
        ChatDTOs.MessageDto msgDto = new ChatDTOs.MessageDto(savedMsg);
        messagingTemplate.convertAndSend("/topic/room/" + room.getId(), msgDto);
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