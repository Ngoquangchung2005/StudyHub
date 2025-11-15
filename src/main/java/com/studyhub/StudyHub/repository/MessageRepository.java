package com.studyhub.StudyHub.repository;



import com.studyhub.StudyHub.entity.Message;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // Tìm tất cả tin nhắn giữa 2 người (cho lịch sử chat)
    // và sắp xếp theo thời gian gửi
    @Query("SELECT m FROM Message m WHERE " +
            "(m.sender.id = :senderId AND m.recipient.id = :recipientId) OR " +
            "(m.sender.id = :recipientId AND m.recipient.id = :senderId)")
    List<Message> findChatHistory(Long senderId, Long recipientId, Sort sort);
}