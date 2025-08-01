package com.kitchenmanager.linebot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
  User findByLineUserId(String lineUserId);

  User findByStudentId(String targetId);
}
