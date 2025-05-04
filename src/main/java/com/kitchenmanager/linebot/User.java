package com.kitchenmanager.linebot;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class User {
    @Id
    private String lineUserId;

    private String studentId;

    public User() {}

    public User(String lineUserId, String studentId) {
        this.lineUserId = lineUserId;
        this.studentId = studentId;
    }

    public String getLineUserId() {
        return lineUserId;
    }

    public void setLineUserId(String lineUserId) {
        this.lineUserId = lineUserId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }
}
