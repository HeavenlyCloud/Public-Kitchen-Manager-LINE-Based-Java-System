package com.kitchenmanager.linebot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
  // All reservations made by a user
  List<Reservation> findByStudentId(String studentId);

  // Reservations with specific status
  List<Reservation> findByStudentIdAndReservationStatus(String studentId, ReservationStatus status);

  // Reservations within a time range
  List<Reservation> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);

  // Combination: user + status + date range
  List<Reservation> findByStudentIdAndReservationStatusAndStartTimeBetween(
      String studentId,
      ReservationStatus status,
      LocalDateTime start,
      LocalDateTime end
  );

}
