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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
        // Chỉ cho phép thêm bạn bè khi tạo nhóm
        for (User u : new HashSet<>(members)) {
            if (u.getId().equals(creator.getId())) continue;
            if (!areFriends(creator, u)) {
                throw new RuntimeException("Chỉ được thêm bạn bè vào nhóm.");
            }
        }

        room.setMembers(members);
        room.setOwner(creator);
        room.getAdmins().add(creator);

        // 3. Lưu nhóm vào Database
        ChatRoom savedRoom = chatRoomRepository.save(room);

        // 4. Gửi sự kiện realtime để các thành viên tự cập nhật danh sách phòng chat (không cần reload trang)
        // QUAN TRỌNG: gửi SAU KHI COMMIT để tránh trường hợp client fetch /api/chat/rooms quá sớm
        // (dẫn tới phải reload mới thấy nhóm)
        runAfterCommit(() -> {
            // Lưu ý: WebSocket user-destination đang định tuyến theo Email (principal.getName())
            for (User member : members) {
                if (member.getEmail() == null) continue;
                ChatDTOs.RoomEventDto evt = new ChatDTOs.RoomEventDto();
                evt.setEventType("ROOM_ADDED");
                evt.setRoomId(savedRoom.getId());
                evt.setActorId(creator.getId());
                evt.setActorName(creator.getName());
                evt.setRoom(new ChatDTOs.ChatRoomDto(savedRoom, member));
                messagingTemplate.convertAndSendToUser(
                        member.getEmail(),
                        "/queue/room-events",
                        evt
                );
            }
        });

        return new ChatRoomDto(savedRoom, creator);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatDTOs.GroupMemberDto> getGroupMembers(Long roomId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Phòng không tồn tại"));
        return room.getMembers().stream()
                .map(u -> new ChatDTOs.GroupMemberDto(
                        u,
                        room.getOwner() != null && room.getOwner().getId().equals(u.getId()),
                        room.getAdmins() != null && room.getAdmins().stream().anyMatch(a -> a.getId().equals(u.getId()))
                ))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addMemberToGroup(Long roomId, Long userId, User adder) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Phòng không tồn tại"));

        assertCanManageGroup(room, adder);

        User newMember = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        if (room.getMembers().add(newMember)) {
            chatRoomRepository.save(room);
            sendSystemMessage(room, adder, adder.getName() + " đã thêm " + newMember.getName() + " vào nhóm.");

            // 1) Người mới được thêm -> nhận phòng mới
            if (newMember.getEmail() != null) {
                ChatDTOs.RoomEventDto evt = new ChatDTOs.RoomEventDto();
                evt.setEventType("ROOM_ADDED");
                evt.setRoomId(room.getId());
                evt.setActorId(adder.getId());
                evt.setActorName(adder.getName());
                evt.setAffectedUserId(newMember.getId());
                evt.setAffectedUsername(newMember.getUsername());
                evt.setRoom(new ChatDTOs.ChatRoomDto(room, newMember));
                runAfterCommit(() -> messagingTemplate.convertAndSendToUser(newMember.getEmail(), "/queue/room-events", evt));
            }

            // 2) Tất cả thành viên (gồm cả newMember) -> cập nhật số thành viên / modal thành viên
            broadcastMembersChanged(room, adder, newMember);
        }
    }

    @Override
    @Transactional
    public void removeMemberFromGroup(Long roomId, Long userId, User remover) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Phòng không tồn tại"));

        assertCanManageGroup(room, remover);
        User memberToRemove = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        if (isOwner(room, memberToRemove)) {
            throw new RuntimeException("Không thể mời chủ nhóm ra khỏi nhóm.");
        }

        if (room.getMembers().remove(memberToRemove)) {
            // nếu user là admin thì gỡ khỏi danh sách admin
            if (room.getAdmins() != null) {
                room.getAdmins().removeIf(a -> a.getId().equals(memberToRemove.getId()));
            }
            chatRoomRepository.save(room);
            sendSystemMessage(room, remover, remover.getName() + " đã mời " + memberToRemove.getName() + " ra khỏi nhóm.");

            // 1) Người bị kick -> xóa phòng khỏi sidebar
            if (memberToRemove.getEmail() != null) {
                ChatDTOs.RoomEventDto evt = new ChatDTOs.RoomEventDto();
                evt.setEventType("ROOM_REMOVED");
                evt.setRoomId(room.getId());
                evt.setActorId(remover.getId());
                evt.setActorName(remover.getName());
                evt.setAffectedUserId(memberToRemove.getId());
                evt.setAffectedUsername(memberToRemove.getUsername());
                runAfterCommit(() -> messagingTemplate.convertAndSendToUser(memberToRemove.getEmail(), "/queue/room-events", evt));
            }

            // 2) Thành viên còn lại -> cập nhật danh sách
            broadcastMembersChanged(room, remover, memberToRemove);
        }
    }



    @Override
    @Transactional
    public void renameGroup(Long roomId, String newName, User actor) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new RuntimeException("Tên nhóm không được để trống.");
        }
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Phòng không tồn tại"));
        assertGroupRoom(room);
        assertCanManageGroup(room, actor);

        room.setName(newName.trim());
        chatRoomRepository.save(room);

        sendSystemMessage(room, actor, actor.getName() + " đã đổi tên nhóm thành \"" + newName.trim() + "\".");
        broadcastRoomUpdated(room, actor);
    }

    @Override
    @Transactional
    public void setGroupAdmin(Long roomId, Long userId, boolean isAdmin, User actor) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Phòng không tồn tại"));
        assertGroupRoom(room);

        if (!isOwner(room, actor)) {
            throw new RuntimeException("Chỉ chủ nhóm mới có thể phân quyền admin.");
        }

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User không tồn tại"));

        if (!isMember(room, target)) {
            throw new RuntimeException("User không phải thành viên nhóm.");
        }
        if (isOwner(room, target) && !isAdmin) {
            throw new RuntimeException("Không thể gỡ quyền admin của chủ nhóm.");
        }

        if (room.getAdmins() == null) {
            room.setAdmins(new HashSet<>());
        }

        if (isAdmin) {
            room.getAdmins().add(target);
            sendSystemMessage(room, actor, actor.getName() + " đã cấp quyền admin cho " + target.getName() + ".");
        } else {
            room.getAdmins().removeIf(a -> a.getId().equals(target.getId()));
            sendSystemMessage(room, actor, actor.getName() + " đã gỡ quyền admin của " + target.getName() + ".");
        }

        chatRoomRepository.save(room);

        // Realtime: cập nhật sidebar + modal
        broadcastMembersChanged(room, actor, target);
        broadcastRoomUpdated(room, actor);
    }

    @Override
    @Transactional
    public void deleteGroup(Long roomId, User actor) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Phòng không tồn tại"));
        assertGroupRoom(room);

        if (!isOwner(room, actor)) {
            throw new RuntimeException("Chỉ chủ nhóm mới có thể xóa nhóm.");
        }

        Set<User> membersSnapshot = new HashSet<>(room.getMembers());

        // Xóa phòng (messages sẽ cascade)
        chatRoomRepository.delete(room);

        // Realtime: thông báo xóa phòng cho tất cả thành viên
        broadcastRoomDeleted(roomId, actor, membersSnapshot);
    }

    /**
     * Gửi WebSocket event SAU KHI transaction commit để client fetch thấy dữ liệu mới ngay.
     * Nếu không có transaction, chạy ngay.
     */
    private void runAfterCommit(Runnable task) {
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try { task.run(); } catch (Exception ignored) {}
                    }
                });
            } else {
                task.run();
            }
        } catch (Exception e) {
            // Fallback: không block luồng chính
            try { task.run(); } catch (Exception ignored) {}
        }
    }

    private void assertGroupRoom(ChatRoom room) {
        if (room.getType() != ChatRoom.RoomType.GROUP) {
            throw new RuntimeException("Thao tác này chỉ áp dụng cho nhóm.");
        }
    }

    private boolean isMember(ChatRoom room, User user) {
        return room.getMembers().stream().anyMatch(m -> m.getId().equals(user.getId()));
    }

    private boolean isOwner(ChatRoom room, User user) {
        return room.getOwner() != null && room.getOwner().getId().equals(user.getId());
    }

    private boolean isGroupAdmin(ChatRoom room, User user) {
        if (isOwner(room, user)) return true;
        return room.getAdmins() != null && room.getAdmins().stream().anyMatch(a -> a.getId().equals(user.getId()));
    }

    private void assertCanManageGroup(ChatRoom room, User actor) {
        if (!isMember(room, actor)) throw new RuntimeException("Bạn không phải thành viên nhóm này");
        if (!isGroupAdmin(room, actor)) throw new RuntimeException("Bạn không có quyền quản trị nhóm");
    }

    private boolean areFriends(User u1, User u2) {
        return friendshipRepository.findRelationship(u1, u2)
                .filter(f -> f.getStatus() == Friendship.FriendshipStatus.ACCEPTED)
                .isPresent();
    }

    private void broadcastRoomUpdated(ChatRoom room, User actor) {
        runAfterCommit(() -> {
            for (User member : room.getMembers()) {
                if (member.getEmail() == null) continue;
                ChatDTOs.RoomEventDto evt = new ChatDTOs.RoomEventDto();
                evt.setEventType("ROOM_UPDATED");
                evt.setRoomId(room.getId());
                evt.setActorId(actor.getId());
                evt.setActorName(actor.getName());
                evt.setRoom(new ChatDTOs.ChatRoomDto(room, member));
                messagingTemplate.convertAndSendToUser(member.getEmail(), "/queue/room-events", evt);
            }
        });
    }

    private void broadcastRoomDeleted(Long roomId, User actor, Set<User> membersSnapshot) {
        runAfterCommit(() -> {
            for (User member : membersSnapshot) {
                if (member.getEmail() == null) continue;
                ChatDTOs.RoomEventDto evt = new ChatDTOs.RoomEventDto();
                evt.setEventType("ROOM_DELETED");
                evt.setRoomId(roomId);
                evt.setActorId(actor.getId());
                evt.setActorName(actor.getName());
                messagingTemplate.convertAndSendToUser(member.getEmail(), "/queue/room-events", evt);
            }
        });
    }


    private void broadcastMembersChanged(ChatRoom room, User actor, User affectedUser) {
        // Gửi MEMBERS_CHANGED đến các thành viên còn lại + người mới (nếu còn trong set)
        runAfterCommit(() -> {
            for (User m : room.getMembers()) {
                if (m.getEmail() == null) continue;
                ChatDTOs.RoomEventDto evt = new ChatDTOs.RoomEventDto();
                evt.setEventType("MEMBERS_CHANGED");
                evt.setRoomId(room.getId());
                evt.setActorId(actor.getId());
                evt.setActorName(actor.getName());
                if (affectedUser != null) {
                    evt.setAffectedUserId(affectedUser.getId());
                    evt.setAffectedUsername(affectedUser.getUsername());
                }
                messagingTemplate.convertAndSendToUser(m.getEmail(), "/queue/room-events", evt);
            }
        });
    }

    private void sendSystemMessage(ChatRoom room, User sender, String text) {
        Message systemMsg = new Message();
        systemMsg.setContent(text);
        systemMsg.setRoom(room);
        systemMsg.setSender(sender);
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

        // Gửi event cho người rời nhóm -> xóa room khỏi sidebar
        if (user.getEmail() != null) {
            ChatDTOs.RoomEventDto evt = new ChatDTOs.RoomEventDto();
            evt.setEventType("ROOM_REMOVED");
            evt.setRoomId(roomId);
            evt.setActorId(user.getId());
            evt.setActorName(user.getName());
            evt.setAffectedUserId(user.getId());
            evt.setAffectedUsername(user.getUsername());
            runAfterCommit(() -> messagingTemplate.convertAndSendToUser(user.getEmail(), "/queue/room-events", evt));
        }

        // Nếu người rời nhóm là chủ nhóm, chuyển quyền cho 1 thành viên còn lại
        if (isOwner(room, user) && !room.getMembers().isEmpty()) {
            User newOwner = room.getMembers().iterator().next();
            room.setOwner(newOwner);
            room.getAdmins().add(newOwner);
            // Broadcast để mọi người cập nhật quyền
            broadcastMembersChanged(room, user, newOwner);
            broadcastRoomUpdated(room, user);
        }

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

            // Thành viên còn lại -> cập nhật số thành viên/modal
            broadcastMembersChanged(room, user, user);
        }
    }
}