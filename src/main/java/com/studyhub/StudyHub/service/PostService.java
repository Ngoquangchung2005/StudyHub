package com.studyhub.StudyHub.service;



import com.studyhub.StudyHub.dto.PostDto;
import com.studyhub.StudyHub.entity.Post;

import java.util.List;

public interface PostService {
    // Định nghĩa hàm tạo bài đăng
    void createPost(PostDto postDto, String username);
    // Lấy tất cả bài đăng, sắp xếp theo thứ tự mới nhất
    List<Post> getAllPostsSortedByDate();
}