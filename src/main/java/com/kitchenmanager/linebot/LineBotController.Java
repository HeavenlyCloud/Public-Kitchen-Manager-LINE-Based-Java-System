@RestController
public class LineBotController {
    @Autowired
    private ReservationService reservationService;

    @Value("${line.channel-secret}")
    private String channelSecret;

    @PostMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestHeader("X-Line-Signature") String signature,
            @RequestBody String payload) {
                
        if (!isValidSignature(payload, signature)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invalid signature");
        }
        
        // Automated replies here:
        // reminder: Please verify line bot account before launch, makes it more official :> 
        String reply = reservationService.processMessage(payload);
        return ResponseEntity.ok(reply);
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
    
}