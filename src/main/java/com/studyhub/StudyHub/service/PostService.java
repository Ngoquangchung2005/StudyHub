package com.studyhub.StudyHub.service;


import com.studyhub.StudyHub.dto.CommentDto;
import com.studyhub.StudyHub.dto.PostDto;
import com.studyhub.StudyHub.entity.Post;
import com.studyhub.StudyHub.entity.User;

import java.security.Principal;
import java.util.List;

public interface PostService {
    // Lấy tất cả bài đăng
    List<Post> getAllPostsSortedByDate();
    // Sửa dòng này: Thêm tham số boolean isOwner
    List<Post> getPostsByUser(User user, boolean isOwner);

    // Tạo bài đăng mới
    void createPost(PostDto postDto, Principal principal);

    // Thêm bình luận
    void addComment(Long postId, CommentDto commentDto, Principal principal);

    // Like hoặc Unlike bài đăng
    void toggleLike(Long postId, Principal principal);
    // === THÊM MỚI ===
    List<Post> searchPosts(String keyword);
    // === THÊM CÁC HÀM MỚI ===
    Post getPostById(Long id); // Lấy bài để hiển thị lên form sửa
    void updatePost(Long postId, PostDto postDto, Principal principal); // Lưu sửa đổi
    void deletePost(Long postId, Principal principal); // Xóa bài
    void deleteComment(Long commentId, Principal principal); // <--- THÊM HÀM NÀY
}