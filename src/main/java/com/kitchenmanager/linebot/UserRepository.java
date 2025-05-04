package com.kitchenmanager.linebot;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {
  User findByLineUserId(String lineUserId);
}
