package com.studyhub.StudyHub.service;

import com.studyhub.StudyHub.entity.Friendship;
import com.studyhub.StudyHub.entity.User;
import com.studyhub.StudyHub.repository.FriendshipRepository;
import com.studyhub.StudyHub.repository.NotificationRepository;
import com.studyhub.StudyHub.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class FriendService {

    @Autowired private FriendshipRepository friendshipRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;

    // Inject NotificationService để dùng chung logic thông báo
    @Autowired private NotificationService notificationService;

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

        // Lưu DB & Gửi Realtime chuẩn format
        notificationService.sendNotification(
                requester,
                addressee,
                requester.getName() + " muốn kết bạn với bạn.",
                "/friends" // Khi bấm vào thông báo sẽ dẫn đến trang Lời mời
        );
    }

    // Chấp nhận kết bạn
    @Transactional
    public void acceptFriendRequest(Long friendshipId, Long userId) {
        Friendship friendship = friendshipRepository.findById(friendshipId).orElseThrow();

        if (!friendship.getAddressee().getId().equals(userId)) {
            throw new RuntimeException("Bạn không có quyền chấp nhận lời mời này");
        }

        friendship.setStatus(Friendship.FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);

        // Thông báo cho người gửi lời mời (requester) biết là mình (addressee) đã đồng ý
        notificationService.sendNotification(
                friendship.getAddressee(),
                friendship.getRequester(),
                friendship.getAddressee().getName() + " đã chấp nhận lời mời kết bạn.",
                "/profile/" + friendship.getAddressee().getUsername() // Bấm vào sẽ sang trang cá nhân bạn bè
        );
    }

    // === BỔ SUNG HÀM TỪ CHỐI KẾT BẠN ===
    @Transactional
    public void declineFriendRequest(Long friendshipId) {
        // Xóa bản ghi Friendship khỏi database
        friendshipRepository.deleteById(friendshipId);
    }

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

    public List<Friendship> getPendingRequests(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return friendshipRepository.findByAddresseeAndStatus(user, Friendship.FriendshipStatus.PENDING);
    }

    @Transactional
    public void unfriend(Long userId, Long friendId) {
        User u1 = userRepository.findById(userId).orElseThrow();
        User u2 = userRepository.findById(friendId).orElseThrow();
        Friendship f = friendshipRepository.findRelationship(u1, u2).orElseThrow(() -> new RuntimeException("Không tìm thấy quan hệ"));
        friendshipRepository.delete(f);
    }

    public String getFriendshipStatus(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) return "SELF";
        User u1 = userRepository.findById(currentUserId).orElseThrow();
        User u2 = userRepository.findById(targetUserId).orElseThrow();
        Optional<Friendship> friendshipOpt = friendshipRepository.findRelationship(u1, u2);
        if (friendshipOpt.isEmpty()) return "NONE";
        Friendship friendship = friendshipOpt.get();
        if (friendship.getStatus() == Friendship.FriendshipStatus.ACCEPTED) return "FRIEND";
        if (friendship.getRequester().getId().equals(currentUserId)) return "SENT";
        else return "RECEIVED";
    }

    public Long getFriendshipId(Long currentUserId, Long targetUserId) {
        User u1 = userRepository.findById(currentUserId).orElseThrow();
        User u2 = userRepository.findById(targetUserId).orElseThrow();
        Optional<Friendship> f = friendshipRepository.findRelationship(u1, u2);
        return f.map(Friendship::getId).orElse(null);
    }
    // Thêm vào trong class FriendService
    public List<User> searchUsers(String keyword, Long currentUserId) {
        return userRepository.searchUsers(keyword, currentUserId);
    }
}