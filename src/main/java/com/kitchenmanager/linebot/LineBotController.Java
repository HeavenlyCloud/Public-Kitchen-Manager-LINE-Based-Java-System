package com.kitchenmanager.linebot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenmanager.linebot.dto.LineWebHookEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@RestController
public class LineBotController {

    @Value("${line.channel-token}")
    private String channelToken;

    @Autowired
    private ReservationService reservationService;

    @Value("${line.channel-secret}")
    private String channelSecret;

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

    @PostMapping("/callback")
    public ResponseEntity<String> callback(

        @RequestHeader("X-Line-Signature") String signature,
        @RequestBody String payload) {

    System.out.println("📥 Raw LINE payload:\n" + payload);

        // if (!isValidSignature(payload, signature)) {
        // return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid signature");
        // }

        try {
            ObjectMapper mapper = new ObjectMapper();
            LineWebHookEvent webhook = mapper.readValue(payload, LineWebHookEvent.class);

            for (LineWebHookEvent.Event event : webhook.events) {
                if (event == null || event.message == null || event.message.text == null || event.replyToken == null) {
                    System.out.println("⚠️ Skipping non-text or malformed event: " + new ObjectMapper().writeValueAsString(event));
                    continue;
                }
            
                String messageText = event.message.text.trim();
                String userId = event.source.userId;
                String reply = reservationService.processMessage(messageText, userId);
            
                System.out.printf("👤 LINE user %s said: \"%s\" → %s\n", userId, messageText, reply);
                sendReply(event.replyToken, reply);
            }
            

            return ResponseEntity.ok("OK");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error processing webhook");
        }
    }

    private boolean isValidSignature(String payload, String signature) {
        try {
            SecretKeySpec key = new SecretKeySpec(channelSecret.getBytes(), "HmacSHA256");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] hash = mac.doFinal(payload.getBytes("UTF-8"));
            String encoded = Base64.getEncoder().encodeToString(hash);
            return encoded.equals(signature);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void sendReply(String replyToken, String messageText) {
        try {
            String json = """
                    {
                        "replyToken": "%s",
                        "messages": [{
                            "type": "text",
                            "text": "%s"
                        }]
                    }
                    """.formatted(replyToken, messageText.replace("\"", "\\\"")); // escape quotes

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(channelToken);

            HttpEntity<String> request = new HttpEntity<>(json, headers);
            ResponseEntity<String> response = new RestTemplate().postForEntity(
                    "https://api.line.me/v2/bot/message/reply", request, String.class);

            System.out.println("📤 Sent LINE reply: " + response.getStatusCode());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // private String extractMessageText(String payload) {
    // if (payload.contains("reserve")) return "reserve";
    // if (payload.contains("cancel")) return "cancel";
    // return "unknown";
    // }
}
