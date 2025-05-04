package com.kitchenmanager.linebot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LineWebHookEvent {
    public List<Event> events;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Event {
        public Source source;
        public Message message;
        public String replyToken;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Source {
        public String userId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        public String type;
        public String text;
    }
}
