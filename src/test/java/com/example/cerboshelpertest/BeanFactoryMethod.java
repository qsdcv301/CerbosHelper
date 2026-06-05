package com.example.cerboshelpertest;

import org.springframework.context.annotation.Bean;

public class BeanFactoryMethod {
    @Bean
    public String sampleBean() {
        return "sample";
    }
}
