package com.studyhub.StudyHub.repository;

import com.studyhub.StudyHub.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// JpaRepository<TênEntity, KiểuDữLiệuCủaKhóaChính>
public interface RoleRepository extends JpaRepository<Role, Long> {
    // Tự động tạo câu lệnh "SELECT * FROM roles WHERE name = ?"
    Optional<Role> findByName(String name);
}