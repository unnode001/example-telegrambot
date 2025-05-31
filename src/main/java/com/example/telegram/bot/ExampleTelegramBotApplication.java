package com.example.telegram.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
// import org.telegram.telegrambots.starter.TelegramBotsContext; // 移除此导入

@SpringBootApplication
// 扫描当前包及其子包，确保Spring能够发现并注册EchoBot
public class ExampleTelegramBotApplication {

    public static void main(String[] args) {
        // 当使用 telegrambots-spring-boot-starter 时，通常不需要显式调用 TelegramBotsContext.init()
        // Spring Boot Starter 会自动处理机器人的初始化和注册。
        SpringApplication.run(ExampleTelegramBotApplication.class, args);
    }
}