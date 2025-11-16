package com.studyhub.StudyHub.repository;

import com.studyhub.StudyHub.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    // Không cần thêm gì, JpaRepository đã có sẵn các method:
    // - findAll(): Lấy tất cả categories
    // - findById(Long id): Tìm category theo ID
    // - save(): Lưu category
}