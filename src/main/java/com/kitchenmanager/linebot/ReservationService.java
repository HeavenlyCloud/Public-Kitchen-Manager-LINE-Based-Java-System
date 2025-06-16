package com.kitchenmanager.linebot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
// import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class ReservationService {

    @Value("${admin.audit.file}")
    private String adminAuditFile;

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

    @Value("${admin.line-user-ids:}")
    private String adminIdsFromProperties;

    @Value("${admin.ids.file}")
    private String adminIdsFile;

    @PostConstruct
    public void loadAdminIdsFromFile() {
        Set<String> combined = new HashSet<>();

        try {
            Path path = Paths.get(adminIdsFile);
            if (Files.exists(path)) {
                List<String> fileIds = Files.readAllLines(path).stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .collect(Collectors.toList());
                combined.addAll(fileIds);
                System.out.println("📂 Loaded admin IDs from file: " + fileIds);
            } else {
                System.out.println("⚠️ Admin ID file not found. Starting with empty file-based list.");
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to load admin IDs from file.");
            e.printStackTrace();
        }

        if (adminIdsFromProperties != null && !adminIdsFromProperties.isBlank()) {
            List<String> propIds = List.of(adminIdsFromProperties.split(","))
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            combined.addAll(propIds);
            System.out.println("⚙️ Loaded admin IDs from properties: " + propIds);
        }

        adminLineUserIds = new ArrayList<>(combined);
        System.out.println("✅ Final admin list: " + adminLineUserIds);
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
            if (lower.startsWith("register")) {
                String newStudentId = messageText.substring(8).trim();
                return "👋 Welcome!\n" + registerStudent(userId, newStudentId);
            }
            return """
                    👋 Welcome to the Kitchen Reservation Bot!

                    Here's what you can do:
                    - !register (yourStudentID)
                    - !reserve yyyy-MM-dd HH:mm>
                    - !cancel
                    - !status
                    - !report <description>
                    - !help → for full list of commands
                    - !admin → if you’re an admin
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

        if (user == null || user.getStudentId() == null || user.getStudentId().isBlank()) {
            return "🛑 You're not fully registered. Please register again using: register <yourID>";
        }

        String studentId = user.getStudentId();

        // 🔐 Admin-only block
        if (lower.startsWith("admin")
                || List.of("list", "clear", "check", "unregister").contains(lower.split(" ")[0])) {

            if (!isAdmin(userId))
                return "🚫 Admin command. Access denied.";
            if (lower.equals("admin help") || lower.equals("admin"))
                return adminHelp();
            if (lower.startsWith("admin unregister")) {
                if (lower.startsWith("admin unregister")) {
                    return adminUnregister(messageText, userId);
                }
            }
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
            if (lower.equals("admin reports"))
                return viewAllReports();
            if (lower.equals("admin view audit"))
                return viewAdminAudit();
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

    public String adminUnregister(String messageText, String issuerId) {
        String[] parts = messageText.split("\\s+");
        if (parts.length != 3) {
            return "⚠️ Usage: admin unregister <studentId>";
        }

        String targetId = parts[2].trim();

        if (!targetId.matches("\\d{8}")) {
            return "⚠️ Invalid student ID format. Must be 8 digits.";
        }

        User user = userRepository.findByStudentId(targetId);
        if (user == null) {
            return "❌ No user found with that student ID.";
        }

        userRepository.delete(user);
        logAdminAction(issuerId, "unregistered student: " + targetId);
        return "🗑️ Successfully unregistered student ID: " + targetId;
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

        if (!studentId.matches("\\d{8}")) {
            return "⚠️ Invalid student ID format. Use 8 digits only.";
        }

        User existing = userRepository.findByLineUserId(lineUserId);
        if (existing != null) {
            return "ℹ️ You're already registered. Use 'status' or 'reserve'.";
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
        logAdminAction("SYSTEM", "cleared " + upcoming.size() + " reservations");
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

    // TODO : Implement actual config reload logic
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
                "- !admin stats\n" +
                "- !admin logs\n" +
                "- !admin reload\n" +
                "- !admin ids\n" +
                "- !admin add <line user id> e.g. !admin add 24113324\n" +
                "- !admin remove <line user id> e.g. !admin remove 24113324\n" +
                "- !admin unregister <studentId>\n" +
                "- !list\n" +
                "- !clear\n" +
                "- !check <yyyy-MM-dd HH:mm> e.g. !check 2023-10-01 12:00\n" +
                "- !admin reports\n";
    }

    public String help() {
        return "🤖 Available commands:\n" +
                "- !register <yourID> e.g. !register 24113324\n" +
                "- !reserve <yyyy-MM-dd HH:mm> e.g. !reserve 2023-10-01 12:00\n" +
                "- !cancel\n" +
                "- !status\n" +
                "- !report <description>\n" +
                "- !help\n" +
                "- !admin (if you’re an admin) :>";
    }

    public String addAdmin(String messageText, String issuerId) {
        String[] parts = messageText.split(" ", 3);
        if (parts.length < 3)
            return "⚠️ Usage: admin add <LINE_USER_ID>";

        String newAdminId = parts[2].trim();
        if (adminLineUserIds.contains(newAdminId))
            return "ℹ️ That user is already an admin.";

        adminLineUserIds.add(newAdminId);
        System.out.println("👮 Admin " + issuerId + " added new admin: " + newAdminId);
        saveAdminIdsToFile();
        logAdminAction(issuerId, "added new admin: " + newAdminId);
        // TODO : Notify the new admin (if needed)
        // lineMessagingService.pushMessage(newAdminId, "You have been added as an
        // admin.");
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
        System.out.println("🛑 Admin " + issuerId + " removed admin: " + removeId);
        saveAdminIdsToFile();
        logAdminAction(issuerId, "removed admin: " + removeId);
        // TODO : Notify the removed admin (if needed)
        // lineMessagingService.pushMessage(removeId, "You have been removed as an
        // admin.");
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

    private void logAdminAction(String userId, String actionDescription) {
        String logEntry = String.format("[%s] Admin %s: %s%n",
                LocalDateTime.now(), userId, actionDescription);
        try {
            Files.writeString(Paths.get(adminAuditFile), logEntry, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
            System.out.println("📝 Logged admin action: " + actionDescription);
        } catch (IOException e) {
            System.err.println("❌ Failed to write admin audit log.");
            e.printStackTrace();
        }
    }

    public String viewAdminAudit() {
        try {
            Path path = Paths.get(adminAuditFile);
            if (!Files.exists(path))
                return "📭 No admin audit log found.";

            List<String> allLines = Files.readAllLines(path);
            int total = allLines.size();
            int start = Math.max(0, total - 10); // Last 10 lines

            List<String> recent = allLines.subList(start, total);
            return "📜 Last Admin Actions:\n" + String.join("\n", recent);

        } catch (IOException e) {
            e.printStackTrace();
            return "❌ Failed to read admin audit log.";
        }
    }

    public String viewAllReports() {
        List<IssueReport> reports = issueReportRepository.findAll();

        if (reports.isEmpty()) {
            return "📭 No issue reports found.";
        }

        StringBuilder sb = new StringBuilder("🛠 Issue Reports:\n");

        for (IssueReport r : reports) {
            sb.append(String.format("- %s (%s): %s\n",
                    r.getStudentId(),
                    r.getTimestamp(),
                    r.getDescription()));
        }

        return sb.toString();
    }

    // TODO: Integrate LIFF front-end here in future.

}
