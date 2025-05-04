package com.kitchenmanager.linebot;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class IssueReport {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String lineUserId;
  private String studentId;
  private String description;
  private LocalDateTime timestamp;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public LocalDateTime getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(LocalDateTime timestamp) {
    this.timestamp = timestamp;
  }

}
