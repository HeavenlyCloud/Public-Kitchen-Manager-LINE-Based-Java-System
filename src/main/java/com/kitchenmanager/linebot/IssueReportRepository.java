package com.kitchenmanager.linebot;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IssueReportRepository extends JpaRepository<IssueReport, Long> {

    // Custom query methods can be defined here if needed
    // For example, to find reports by user ID or status
    List<IssueReport> findByLineUserId(String lineUserId);
    List<IssueReport> findByStudentId(String studentId);
    List<IssueReport> findByDescriptionContaining(String keyword);
    List<IssueReport> findByTimestampBetween(LocalDateTime start, LocalDateTime end);       
}