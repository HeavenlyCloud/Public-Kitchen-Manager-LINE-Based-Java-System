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
    private UserRepository userRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    public String processMessage(String messageText, String userId) {

        updateCompletedReservations();
        
        String lower = messageText.toLowerCase().trim();

        if (lower.startsWith("register")) {
            String newStudentId = messageText.substring(8).trim();
            return registerStudent(userId, newStudentId);
        }

        User user = userRepository.findByLineUserId(userId);
        if (!lower.startsWith("register") && user == null) {
            return "🛑 Please register your student ID first using: register <yourID>";
        }

        String studentId = user != null ? user.getStudentId() : null;

        if (lower.startsWith("report")) {
            String description = messageText.substring(6).trim();
            return reportIssue(userId, studentId, description);
        }

        switch (lower) {
            case "reserve":
                return reserve(userId, studentId, messageText);
            case "cancel":
                return cancel(userId);
            case "status":
                return getStatus(userId);
            default:
                return "🤖 I didn't understand that. Try 'reserve', 'cancel', 'status', or 'report'.";
        }
    }

    @Value("${reservation.cooldown.hours}")
    private int cooldownHours;

    public String reserve(String userId, String studentId, String messageText) {
        try {
            String[] parts = messageText.split(" ", 2);
            LocalDateTime startTime;

            if (parts.length == 2) {
                startTime = LocalDateTime.parse(parts[1].trim(), TimeUtil.FORMATTER);
            } else {
                startTime = LocalDateTime.now();
            }

            LocalDateTime endTime = startTime.plusHours(1);

            // Check if user is spamming reservations (cooldown)
            Reservation lastConfirmed = reservationRepository
                    .findTopByLineUserIdAndReservationStatusOrderByStartTimeDesc(userId, ReservationStatus.CONFIRMED);
            if (lastConfirmed != null &&
                    lastConfirmed.getEndTime().isAfter(LocalDateTime.now().minusHours(cooldownHours))) {
                return "⏳ You need to wait before making another reservation.";
            }

            // Prevent duplicates
            boolean alreadyReserved = reservationRepository.existsByLineUserIdAndReservationStatus(userId,
                    ReservationStatus.CONFIRMED);
            if (alreadyReserved) {
                return "❌ You already have a reservation. Cancel it first.";
            }

            // Check number of overlapping reservations
            var overlapping = reservationRepository
                    .findByReservationStatusAndStartTimeBetween(ReservationStatus.CONFIRMED, startTime.minusHours(1),
                            endTime);
            long conflictCount = overlapping.stream()
                    .filter(r -> r.getEndTime().isAfter(startTime) && r.getStartTime().isBefore(endTime))
                    .count();

            if (conflictCount >= 3) {
                return "🚫 That time slot already has 3 users. Please pick another.";
            }

            // Proceed to reserve
            Reservation reservation = new Reservation();
            reservation.setLineUserId(userId);
            reservation.setStudentId(studentId);
            reservation.setStartTime(startTime);
            reservation.setEndTime(endTime);
            reservation.setReservationStatus(ReservationStatus.CONFIRMED);
            reservationRepository.save(reservation);

            String formattedStart = TimeUtil.format(startTime);
            String formattedEnd = TimeUtil.format(endTime);

            return String.format("✅ Reserved!\n🕒 %s to %s\n👥 You’re #%d in this time slot.", formattedStart,
                    formattedEnd, conflictCount + 1);

        } catch (Exception e) {
            return "⚠️ Invalid format. Use:\nreserve yyyy-MM-dd HH:mm";
        }
    }

    public String cancel(String userId) {
        Optional<Reservation> optional = reservationRepository.findTopByLineUserIdOrderByStartTimeDesc(userId);
        if (optional.isPresent()) {
            Reservation reservation = optional.get();
            reservation.setReservationStatus(ReservationStatus.CANCELLED);
            reservationRepository.save(reservation);
            return "❌ Your reservation was cancelled.";
        } else {
            return "⚠️ No reservation found to cancel.";
        }
    }

    public String getStatus(String userId) {
        Optional<Reservation> optional = reservationRepository.findTopByLineUserIdOrderByStartTimeDesc(userId);
        if (optional.isPresent()) {
            Reservation reservation = optional.get();
            return "📋 Your reservation status: " + reservation.getReservationStatus().name();
        } else {
            return "ℹ️ No reservation found.";
        }
    }

    @Autowired
    private IssueReportRepository issueReportRepository;

    public String reportIssue(String userId, String studentId, String description) {
        if (description == null || description.isBlank()) {
            return "⚠️ Please describe the issue after 'report'.";
        }

        IssueReport report = new IssueReport();
        report.setLineUserId(userId);
        report.setStudentId(studentId);
        report.setDescription(description);
        report.setTimestamp(LocalDateTime.now());

        issueReportRepository.save(report);
        return "🛠️ Your report has been received. Thank you!";
    }

    public String registerStudent(String lineUserId, String studentId) {
        if (studentId.isBlank()) {
            return "❗ Please provide your student ID after 'register'.";
        }

        User user = new User(lineUserId, studentId);
        userRepository.save(user);
        return "✅ Registration successful! Your student ID is now linked.";
    }

    public void updateCompletedReservations() {
        LocalDateTime now = LocalDateTime.now();
        var expired = reservationRepository.findByReservationStatusAndEndTimeBefore(
                ReservationStatus.CONFIRMED, now);

        for (Reservation r : expired) {
            r.setReservationStatus(ReservationStatus.COMPLETED);
        }

        reservationRepository.saveAll(expired);

        if (!expired.isEmpty()) {
            System.out.println("🔁 Marked " + expired.size() + " reservation(s) as COMPLETED.");
        }
    }

}
