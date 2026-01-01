package com.studyhub.StudyHub.service;

import com.studyhub.StudyHub.dto.NotificationDto;
import com.studyhub.StudyHub.entity.Friendship;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.FriendshipRepository;
import com.studyhub.StudyHub.repository.NotificationRepository;
import com.studyhub.StudyHub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FriendService {

    @Autowired private FriendshipRepository friendshipRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate; // Dùng để gửi WebSocket
    // THÊM: Repository thông báo
    @Autowired private NotificationRepository notificationRepository;

    // Gửi lời mời kết bạn
    @Transactional
    public void sendFriendRequest(Long requesterId, Long addresseeId) {
        if (requesterId.equals(addresseeId)) throw new RuntimeException("Không thể kết bạn với chính mình");

        User requester = userRepository.findById(requesterId).orElseThrow();
        User addressee = userRepository.findById(addresseeId).orElseThrow();

        if (friendshipRepository.findRelationship(requester, addressee).isPresent()) {
            throw new RuntimeException("Đã tồn tại mối quan hệ hoặc lời mời.");
        }

        Friendship friendship = new Friendship();
        friendship.setRequester(requester);
        friendship.setAddressee(addressee);
        friendship.setStatus(Friendship.FriendshipStatus.PENDING);
        friendshipRepository.save(friendship);


        // REALTIME: Gửi thông báo cho người nhận
        messagingTemplate.convertAndSendToUser(
                addressee.getUsername(), // Gửi tới username của người nhận
                "/queue/notify",
                "FRIEND_REQUEST" // Client sẽ nghe message này để hiện popup hoặc reload
        );
    }

    // Chấp nhận kết bạn
    @Transactional
    public void acceptFriendRequest(Long friendshipId, Long userId) {
        Friendship friendship = friendshipRepository.findById(friendshipId).orElseThrow();

        // Chỉ người nhận mới được chấp nhận
        if (!friendship.getAddressee().getId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền chấp nhận lời mời này");
        }

        friendship.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);

        // REALTIME: Thông báo cho người gửi là đã được chấp nhận
        messagingTemplate.convertAndSendToUser(
                friendship.getRequester().getUsername(),
                "/queue/notify",
                "FRIEND_ACCEPTED"
        );
    }

    // Lấy danh sách bạn bè (để hiển thị ở Danh bạ)
    public List<User> getFriendList(Long userId) {
        List<Friendship> friendships = friendshipRepository.findAllFriends(userId);
        List<User> friends = new ArrayList<>();
        for (Friendship f : friendships) {
            if (f.getRequester().getId().equals(userId)) {
                friends.add(f.getAddressee());
            } else {
                friends.add(f.getRequester());
            }
        }
        return friends;
    }

    // Lấy danh sách lời mời kết bạn đang chờ
    public List<Friendship> getPendingRequests(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return friendshipRepository.findByAddresseeAndStatus(user, Friendship.FriendshipStatus.PENDING);
    }

    // Xóa bạn hoặc hủy lời mời
    @Transactional
    public void unfriend(Long userId, Long friendId) {
        User u1 = userRepository.findById(userId).orElseThrow();
        User u2 = userRepository.findById(friendId).orElseThrow();
        Friendship f = friendshipRepository.findRelationship(u1, u2).orElseThrow(() -> new RuntimeException("Không tìm thấy quan hệ"));
        friendshipRepository.delete(f);
    }
    // Hàm kiểm tra trạng thái để hiển thị nút bấm trên Profile
    public String getFriendshipStatus(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            return "SELF"; // Xem trang của chính mình
        }

        User u1 = userRepository.findById(currentUserId).orElseThrow();
        User u2 = userRepository.findById(targetUserId).orElseThrow();

        Optional<Friendship> friendshipOpt = friendshipRepository.findRelationship(u1, u2);

        if (friendshipOpt.isEmpty()) {
            return "NONE"; // Chưa kết bạn
        }

        Friendship friendship = friendshipOpt.get();

        if (friendship.getStatus() == Friendship.FriendshipStatus.ACCEPTED) {
            return "FRIEND"; // Đã là bạn bè
        }

        // Nếu đang chờ (PENDING)
        if (friendship.getRequester().getId().equals(currentUserId)) {
            return "SENT"; // Mình là người gửi lời mời
        } else {
            return "RECEIVED"; // Họ gửi lời mời cho mình
        }
    }

    // Hàm lấy ID của Friendship (để dùng cho nút Chấp nhận)
    public Long getFriendshipId(Long currentUserId, Long targetUserId) {
        User u1 = userRepository.findById(currentUserId).orElseThrow();
        User u2 = userRepository.findById(targetUserId).orElseThrow();
        Optional<Friendship> f = friendshipRepository.findRelationship(u1, u2);
        return f.map(Friendship::getId).orElse(null);
    }
}