package com.kitchenmanager.linebot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ReservationService {

    @Autowired
    private ReservationRepository reservationRepository;

    public String processMessage(String payload) {

        if (payload.toLowerCase().contains("reserve")) {
            
            // Step 1: Create dummy reservation
            Reservation reservation = new Reservation();
            reservation.setStudentId("student123"); // Replace later with actual ID
            reservation.setLineUserId("lineUser123"); // Replace later with real LINE ID
            reservation.setStartTime(LocalDateTime.now().plusHours(1));
            reservation.setEndTime(LocalDateTime.now().plusHours(2));
            reservation.setReservationStatus(ReservationStatus.CONFIRMED);

            // Step 2: Save to database
            reservationRepository.save(reservation);

            // Step 3: Return message
            return "✅ Reservation confirmed from 1pm to 2pm.";
        }

        return "❓ Sorry, I didn't understand. Try typing 'reserve'.";
    }
}
