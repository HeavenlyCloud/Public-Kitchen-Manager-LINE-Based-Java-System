package com.kitchenmanager.linebot.dto;

import java.util.List;

public class LineWebHookEvent {
  public List<Event> events;

  public static class Event {
    public Source source;
    public Message message;

    public static class Source {
      public String userId;
    }

    public static class Message {
      public String type;
      public String text;
    }
  }
}
