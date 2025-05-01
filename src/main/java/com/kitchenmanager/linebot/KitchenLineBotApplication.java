package com.kitchenmanager.linebot; 

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KitchenLineBotApplication {
    public static void main(String[] args) {

        System.out.println("Kitchen Manager Bot started"); // Debugging line to check if the application starts correctly
        SpringApplication.run(KitchenLineBotApplication.class, args); 
    }
}