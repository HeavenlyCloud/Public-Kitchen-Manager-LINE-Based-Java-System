package com.kitchenmanager.linebot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ReservationService {

    @Autowired
    private ReservationRepository reservationRepository;

    public String processMessage(String messageText, String userId, String studentId) {
        switch (messageText.toLowerCase()) {
            case "reserve":
                return reserve(userId, studentId);
            case "cancel":
                return cancel(userId);
            case "status":
                return getStatus(userId);
            case "report":
                return "📊 Reporting feature is not implemented yet.";
            default:
                return "🤖 I didn't understand that. Try 'reserve', 'cancel', or 'status'.";
        }
    }

    @Value("${reservation.cooldown.hours}")
    private int cooldownHours;

    public String reserve(String userId, String studentId) {

        long activeReservations = reservationRepository.countByReservationStatusAndEndTimeAfter(ReservationStatus.CONFIRMED, LocalDateTime.now());

        if (activeReservations >= 5) {
            return "🚫 Sorry, the kitchen is full right now. Please try again later.";
        }
        


        boolean alreadyReserved = reservationRepository.existsByLineUserIdAndReservationStatus(userId, ReservationStatus.CONFIRMED);
        
        Reservation lastConfirmedReservation = reservationRepository.findTopByLineUserIdAndReservationStatusOrderByStartTimeDesc(userId, ReservationStatus.CONFIRMED);
        
        if (lastConfirmedReservation != null && lastConfirmedReservation.getEndTime().isAfter(LocalDateTime.now().minusHours(cooldownHours)))  {
            return "⏳ You need to wait before making another reservation.";
        }

        if (alreadyReserved) {
            return "❌ You already have a reservation. Please cancel it before making a new one.";
        }

        Reservation reservation = new Reservation();
        reservation.setLineUserId(userId);
        reservation.setStudentId(studentId);
        reservation.setStartTime(LocalDateTime.now());
        reservation.setEndTime(LocalDateTime.now().plusHours(1));
        reservation.setReservationStatus(ReservationStatus.CONFIRMED);

        reservationRepository.save(reservation);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
        String formattedStart = reservation.getStartTime().format(formatter);
        String formattedEnd = reservation.getEndTime().format(formatter);

        return String.format("✅ Reservation confirmed from %s to %s.", formattedStart, formattedEnd);
    } 

    public String cancel(String userId){
        Optional <Reservation> optional = reservationRepository.findTopByLineUserIdOrderByStartTimeDesc(userId);
        if (optional.isPresent()) {
            Reservation reservation = optional.get();
            reservation.setReservationStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(reservation);
            return "❌ Your reservation was cancelled.";
        } else {
            return "⚠️ No reservation found to cancel.";
        }
    }

    public String getStatus (String userId) {
        Optional<Reservation> optional = reservationRepository.findTopByLineUserIdOrderByStartTimeDesc(userId);
        if (optional.isPresent()) {
            Reservation reservation = optional.get();
            return "📋 Your reservation status: " + reservation.getReservationStatus().name();
        } else {
            return "ℹ️ No reservation found.";
        }
    }
}
