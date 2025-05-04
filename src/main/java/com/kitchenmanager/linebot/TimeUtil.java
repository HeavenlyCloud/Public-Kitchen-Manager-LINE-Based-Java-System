package com.kitchenmanager.linebot;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class TimeUtil {
  public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  public static String format (LocalDateTime dateTime) {
      return dateTime.format(FORMATTER);
  }
}