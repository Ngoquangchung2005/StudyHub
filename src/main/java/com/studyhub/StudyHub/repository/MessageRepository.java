package com.studyhub.StudyHub.repository;


import com.studyhub.StudyHub.entity.Message;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // Sửa lỗi 500 (LazyInitializationException) bằng JOIN FETCH
    @Query("SELECT m FROM Message m JOIN FETCH m.sender WHERE m.room.id = :roomId")
    List<Message> findByRoomIdWithSender(Long roomId, Sort sort);
}