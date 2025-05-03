package com.kitchenmanager.linebot;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Reservation {
  
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String studentId;
  private String lineUserId;
  private LocalDateTime startTime;
  private LocalDateTime endTime;

  @Enumerated(EnumType.STRING)
  private ReservationStatus reservationStatus = ReservationStatus.PENDING;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getStudentId() {
    return studentId;
  }

  public void setStudentId(String studentId) {
    this.studentId = studentId;
  }  

  public String getLineUserId() {
    return lineUserId;
  }

  public void setLineUserId(String lineUserId) {
    this.lineUserId = lineUserId;
  }

  public LocalDateTime getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalDateTime startTime) {
    this.startTime = startTime;
  }

  public LocalDateTime getEndTime() {
    return endTime;
  }

  public void setEndTime(LocalDateTime endTime) {
    this.endTime = endTime;
  }

  @Override
  public String toString() {
    return "Reservation{" +
            "id=" + id +
            ", studentId='" + studentId + '\'' +
            ", lineUserId='" + lineUserId + '\'' +
            ", startTime=" + startTime +
            ", endTime=" + endTime +
            ", ReservationStatus='" + reservationStatus + '\'' +
            '}';
  }

  public ReservationStatus getReservationStatus() {
    return reservationStatus;
  }
  public void setReservationStatus(ReservationStatus reservationStatus) {
    this.reservationStatus = reservationStatus;
  }


  public ReservationStatus getStatus() {
    return reservationStatus;
  }
  
  public void setStatus(ReservationStatus status) {
    this.reservationStatus = status;
  }


}