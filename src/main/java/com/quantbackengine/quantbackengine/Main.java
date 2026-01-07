package com.quantbackengine.quantbackengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {
    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
        System.out.println("\n🚀 QuantBackEngine Web Server Started!");
        System.out.println("📊 Open your browser to: http://localhost:8080");
        System.out.println("💡 Upload a CSV file and start backtesting!\n");
    }
}
