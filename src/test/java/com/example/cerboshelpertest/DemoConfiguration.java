package com.example.cerboshelpertest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoConfiguration {
    @Bean
    public String sampleBean() {
        return "sample";
    }
}
