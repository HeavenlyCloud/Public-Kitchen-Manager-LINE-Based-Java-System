package com.kitchenmanager.linebot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitchenmanager.linebot.dto.LineWebHookEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@RestController
public class LineBotController {

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

        // if (!isValidSignature(payload, signature)) {
        //     return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid signature");
        // }

        try {
            ObjectMapper mapper = new ObjectMapper();
            LineWebHookEvent webhook = mapper.readValue(payload, LineWebHookEvent.class);

            for (LineWebHookEvent.Event event : webhook.events) {
                String messageText = event.message.text;
                String userId = event.source.userId;

                String reply = reservationService.processMessage(messageText, userId); // studentId temporary 
                
                return ResponseEntity.ok(reply); 
            }

            return ResponseEntity.ok("No events handled");

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

    // private String extractMessageText(String payload) {
    //     if (payload.contains("reserve")) return "reserve";
    //     if (payload.contains("cancel")) return "cancel";
    //     return "unknown";
    // }
}
