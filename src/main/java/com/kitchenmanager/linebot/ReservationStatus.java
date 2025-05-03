package com.kitchenmanager.linebot;

import jakarta.persistence.*;

public enum ReservationStatus {
  PENDING,
  CONFIRMED,
  CANCELLED,
  COMPLETED,
  NO_SHOW,
}
