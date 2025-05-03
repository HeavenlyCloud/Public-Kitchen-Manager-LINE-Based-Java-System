package com.kitchenmanager.linebot; 

import org.springframework.context.ApplicationContext;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "com.kitchenmanager.linebot")
public class KitchenLineBotApplication {
    public static void main(String[] args) {

        System.out.println("Kitchen Manager Bot started");
        SpringApplication.run(KitchenLineBotApplication.class, args); 
        
        
    }

    @Bean
    public CommandLineRunner showBeans(ApplicationContext ctx) {
    return args -> {
        for (String name : ctx.getBeanDefinitionNames()) {
            if (name.contains("line")) {
                System.out.println("👀 Found bean: " + name);
            }
        }
    };
    

    }

}