package com.kitchenmanager.linebot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
  // All reservations made by a user
  List<Reservation> findByStudentId(String studentId);
  List<Reservation> findByLineUserId(String lineUserId);

  // Reservations with specific status
  List<Reservation> findByStudentIdAndReservationStatus(String studentId, ReservationStatus status);
  List<Reservation> findByLineUserIdAndReservationStatus(String lineUserId, ReservationStatus status);


  // Reservations within a time range
  List<Reservation> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);

  // Combination: user + status + date range
  List<Reservation> findByStudentIdAndReservationStatusAndStartTimeBetween(
      String studentId,
      ReservationStatus status,
      LocalDateTime start,
      LocalDateTime end
  );

  boolean existsByLineUserIdAndReservationStatus(String userId, ReservationStatus confirmed);
  Optional<Reservation> findTopByLineUserIdOrderByStartTimeDesc(String userId);

}
