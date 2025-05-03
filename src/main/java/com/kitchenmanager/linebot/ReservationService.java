package com.kitchenmanager.linebot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ReservationService {

    @Autowired
    private ReservationRepository reservationRepository;

    public String processMessage(String messageText, String userId) {
        switch (messageText.toLowerCase()) {
            case "reserve":
                return reserve(userId);
            case "cancel":
                return cancel(userId);
            case "status":
                return getStatus(userId);
            default:
                return "🤖 I didn't understand that. Try 'reserve', 'cancel', or 'status'.";
        }
    }

    public String reserve(String userId) {
        boolean alreadyReserved = reservationRepository.existsByLineUserIdAndReservationStatus(userId, ReservationStatus.CONFIRMED);

        if (alreadyReserved) {
            return "❌ You already have a reservation. Please cancel it before making a new one.";
        }

        Reservation reservation = new Reservation();
        reservation.setLineUserId(userId);
        reservation.setStartTime(LocalDateTime.now());
        reservation.setEndTime(LocalDateTime.now().plusHours(1));
        reservation.setReservationStatus(ReservationStatus.CONFIRMED);

        reservationRepository.save(reservation);
        return "✅ Reservation confirmed from 1pm to 2pm.";

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
