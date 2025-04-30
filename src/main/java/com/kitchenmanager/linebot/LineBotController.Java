@RestController
public class LineBotController {
    @Autowired
    private ReservationService reservationService;

    @PostMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestHeader("X-Line-Signature") String signature,
            @RequestBody String payload) {
        // Automated replies here:
        // reminder: Please verify line bot account before launch, makes it more official :> 
        String reply = reservationService.processMessage(payload);
        return ResponseEntity.ok(reply);
    }
}