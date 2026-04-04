package com.thethirdlicense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication(scanBasePackages = "com.thethirdlicense")
public class TheThirdLicenseApplication {
    public static void main(String[] args) { 
        SpringApplication.run(TheThirdLicenseApplication.class, args);
    }
}
