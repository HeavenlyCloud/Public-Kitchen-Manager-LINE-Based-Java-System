package com.kitchenmanager.linebot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
// import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    private Set<String> greetedUsers = new HashSet<>();

    @PostConstruct
    public void loadGreetedUsers() {
        try {
            Path path = Paths.get(greetedUsersFile);
            if (Files.exists(path)) {
                greetedUsers = Files.readAllLines(path).stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .collect(Collectors.toSet());
            }
            System.out.println("👋 Loaded greeted users: " + greetedUsers.size());
        } catch (IOException e) {
            System.err.println("❌ Failed to load greeted users.");
            e.printStackTrace();
        }
    }

    @Value("${greeted.users.file}")
    private String greetedUsersFile;

    private void saveGreetedUser(String userId) {
        if (greetedUsers.contains(userId))
            return;

        greetedUsers.add(userId);
        try {
            Files.write(Paths.get(greetedUsersFile), greetedUsers);
            System.out.println("📥 Greeted new user: " + userId);
        } catch (IOException e) {
            System.err.println("❌ Failed to save greeted user.");
            e.printStackTrace();
        }
    }

    @Value("${admin.ids.file}")
    private String adminIdsFile;

    @PostConstruct
    public void loadAdminIdsFromFile() {

        try {
            Path path = Paths.get(adminIdsFile);
            if (Files.exists(path)) {
                adminLineUserIds = Files.readAllLines(path).stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .toList();
                System.out.println("✅ Loaded admin IDs: " + adminLineUserIds);
            } else {
                adminLineUserIds = new ArrayList<>();
                System.out.println("⚠️ Admin ID file not found. Starting with empty list.");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load admin IDs from file", e);
        }
    }

    @Value("#{'${admin.line-user-ids}'.split(',')}")
    private List<String> adminLineUserIds;

    private boolean isAdmin(String userId) {
        return adminLineUserIds.contains(userId);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    public String processMessage(String messageText, String userId) {
        updateCompletedReservations();
        String lower = messageText.toLowerCase().trim();

        if (!greetedUsers.contains(userId)) {
            saveGreetedUser(userId);
            return """
                    👋 Welcome to the Kitchen Reservation Bot!

                    Here's what you can do:
                    - register <yourStudentID>
                    - reserve <yyyy-MM-dd HH:mm>
                    - cancel | status | report <description>
                    - help → for full list of commands
                    - admin → if you’re an admin
                    """;
        }

        if (lower.startsWith("register")) {
            String newStudentId = messageText.substring(8).trim();
            return registerStudent(userId, newStudentId);
        }

        User user = userRepository.findByLineUserId(userId);
        if (!lower.startsWith("register") && user == null) {
            return "🛑 Please register your student ID first using: register <yourID>";
        }

        String studentId = user.getStudentId();

        // 🔐 Admin-only block
        if (lower.startsWith("admin") || List.of("list", "clear", "check").contains(lower.split(" ")[0])) {
            if (!isAdmin(userId))
                return "🚫 Admin command. Access denied.";

            if (lower.equals("admin"))
                return adminHelp();
            if (lower.equals("admin stats"))
                return adminStats();
            if (lower.equals("admin logs"))
                return adminLogs();
            if (lower.equals("admin reload"))
                return reloadConfig();
            if (lower.equals("admin ids"))
                return getAdminLineUserIds();
            if (lower.startsWith("admin add"))
                return addAdmin(messageText, userId);
            if (lower.startsWith("admin remove"))
                return removeAdmin(messageText, userId);
            if (lower.equals("list"))
                return listActiveReservations();
            if (lower.equals("clear"))
                return clearAllReservations();
            if (lower.startsWith("check"))
                return checkReservationsAt(messageText);
            return "⚠️ Unknown admin command.";
        }

        // 👥 User commands
        switch (lower.split(" ")[0]) {
            case "reserve":
                return reserve(userId, studentId, messageText);
            case "cancel":
                return cancel(userId);
            case "status":
                return getStatus(userId);
            case "report":
                return reportIssue(userId, studentId, messageText.substring(6).trim());
            case "help":
                return help();
            default:
                return "🤖 I didn't understand that.\nTry 'help' to see commands, or 'register <ID>' to begin."
                        + "\n\n" +
                        "If you are an admin, use 'admin' for more options.";
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

    public String listActiveReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> upcoming = reservationRepository
                .findByReservationStatusAndEndTimeAfter(ReservationStatus.CONFIRMED, now);

        if (upcoming.isEmpty()) {
            return "📭 No upcoming reservations.";
        }

        StringBuilder sb = new StringBuilder("📅 Upcoming Reservations:\n");
        for (Reservation r : upcoming) {
            sb.append(String.format("- %s (%s → %s)\n",
                    r.getStudentId(),
                    TimeUtil.format(r.getStartTime()),
                    TimeUtil.format(r.getEndTime())));
        }
        return sb.toString();
    }

    public String checkReservationsAt(String messageText) {
        try {
            String[] parts = messageText.split(" ", 2);
            if (parts.length < 2) {
                return "⚠️ Use: check yyyy-MM-dd HH:mm";
            }

            LocalDateTime checkTime = LocalDateTime.parse(parts[1].trim(), TimeUtil.FORMATTER);

            List<Reservation> overlapping = reservationRepository
                    .findByReservationStatusAndStartTimeBetween(
                            ReservationStatus.CONFIRMED,
                            checkTime.minusHours(1),
                            checkTime.plusHours(1));

            List<Reservation> actual = overlapping.stream()
                    .filter(r -> r.getEndTime().isAfter(checkTime) && r.getStartTime().isBefore(checkTime))
                    .toList();

            if (actual.isEmpty()) {
                return "🕐 No reservations found at that time.";
            }

            StringBuilder sb = new StringBuilder("👥 Reservations at " + TimeUtil.format(checkTime) + ":\n");
            for (Reservation r : actual) {
                sb.append("- ").append(r.getStudentId()).append("\n");
            }
            return sb.toString();

        } catch (Exception e) {
            return "⚠️ Invalid format. Use: check yyyy-MM-dd HH:mm";
        }
    }

    public String clearAllReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> upcoming = reservationRepository
                .findByReservationStatusAndEndTimeAfter(ReservationStatus.CONFIRMED, now);

        if (upcoming.isEmpty()) {
            return "📭 No active reservations to clear.";
        }

        for (Reservation r : upcoming) {
            r.setReservationStatus(ReservationStatus.CANCELLED);
        }
        reservationRepository.saveAll(upcoming);
        return "🧹 Cleared " + upcoming.size() + " upcoming reservation(s).";
    }

    public String adminStats() {
        long totalUsers = userRepository.count();
        long totalReservations = reservationRepository.count();
        long activeReservations = reservationRepository.countByReservationStatusAndEndTimeAfter(
                ReservationStatus.CONFIRMED, LocalDateTime.now());
        long completed = reservationRepository.countByReservationStatus(ReservationStatus.COMPLETED);
        long cancelled = reservationRepository.countByReservationStatus(ReservationStatus.CANCELLED);
        long reports = issueReportRepository.count();

        return String.format("""
                📈 Admin Stats:
                👤 Users: %d
                📅 Reservations: %d (Active: %d, Completed: %d, Cancelled: %d)
                🛠 Reports: %d
                """, totalUsers, totalReservations, activeReservations, completed, cancelled, reports);
    }

    public String adminLogs() {
        List<Reservation> recent = reservationRepository
                .findTop10ByOrderByStartTimeDesc();

        if (recent.isEmpty())
            return "📭 No reservation logs.";

        StringBuilder sb = new StringBuilder("📜 Last 10 Reservations:\n");
        for (Reservation r : recent) {
            sb.append(String.format("- %s (%s → %s) [%s]\n",
                    r.getStudentId(),
                    TimeUtil.format(r.getStartTime()),
                    TimeUtil.format(r.getEndTime()),
                    r.getReservationStatus()));
        }
        return sb.toString();
    }

    public String reloadConfig() {
        // In a real Spring Boot app, this would trigger an actual reload.
        return "🔁 Reloaded configuration (simulated).";
    }

    public String getAdminLineUserIds() {
        return String.join(", ", adminLineUserIds);
    }

    public void setAdminLineUserIds(List<String> adminLineUserIds) {
        this.adminLineUserIds = adminLineUserIds;
    }

    public String adminHelp() {
        return "🤖 Admin commands:\n" +
                "- admin stats\n" +
                "- admin logs\n" +
                "- admin reload\n" +
                "- admin ids\n" +
                "- admin add <LINE_USER_ID>\n" +
                "- admin remove <LINE_USER_ID>\n" +
                "- list\n" +
                "- clear\n" +
                "- check <yyyy-MM-dd HH:mm>";
    }

    public String help() {
        return "🤖 Available commands:\n" +
                "- register <yourID>\n" +
                "- reserve <yyyy-MM-dd HH:mm>\n" +
                "- cancel\n" +
                "- status\n" +
                "- report <description>\n" +
                "- help\n" +
                "- admin (if you’re an admin)";
    }

    public String addAdmin(String messageText, String issuerId) {
        String[] parts = messageText.split(" ", 3);
        if (parts.length < 3)
            return "⚠️ Usage: admin add <LINE_USER_ID>";

        String newAdminId = parts[2].trim();
        if (adminLineUserIds.contains(newAdminId))
            return "ℹ️ That user is already an admin.";

        adminLineUserIds.add(newAdminId);
        // Optional: Log who added it
        System.out.println("👮 Admin " + issuerId + " added new admin: " + newAdminId);
        saveAdminIdsToFile();
        return "✅ Added new admin: " + newAdminId;
    }

    public String removeAdmin(String messageText, String issuerId) {
        String[] parts = messageText.split(" ", 3);
        if (parts.length < 3)
            return "⚠️ Usage: admin remove <LINE_USER_ID>";

        String removeId = parts[2].trim();
        if (!adminLineUserIds.contains(removeId))
            return "⚠️ That user is not an admin.";

        if (removeId.equals(issuerId))
            return "🚫 You cannot remove yourself.";

        adminLineUserIds.remove(removeId);
        // Optional: Log who removed whom
        System.out.println("🛑 Admin " + issuerId + " removed admin: " + removeId);
        saveAdminIdsToFile();
        return "✅ Removed admin: " + removeId;

    }

    private void saveAdminIdsToFile() {
        try {
            Files.write(Paths.get(adminIdsFile), adminLineUserIds);
            System.out.println("💾 Saved admin IDs to file.");
        } catch (IOException e) {
            System.err.println("❌ Failed to save admin IDs to file.");
            e.printStackTrace();
        }
    }

}
