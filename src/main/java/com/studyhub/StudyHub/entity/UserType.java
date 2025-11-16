package com.studyhub.StudyHub.entity;

public enum UserType {
    STUDENT("Sinh viên"),
    LECTURER("Giảng viên"),
    OTHER("Khác");

    private final String displayName;

    UserType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}