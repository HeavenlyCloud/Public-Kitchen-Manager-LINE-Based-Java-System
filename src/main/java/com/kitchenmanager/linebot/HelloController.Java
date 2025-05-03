package com.kitchenmanager.linebot;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {
    
    @GetMapping("/status")
    public String status() {
        return "Kitchen Manager Bot is running!";
    }

    // @GetMapping("/ping")
    // public String ping() {
    //     return "pong";
    // }

}