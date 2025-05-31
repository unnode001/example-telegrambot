package com.example.telegram.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExampleTelegramBotApplication {

    private static final Logger logger = LoggerFactory.getLogger(ExampleTelegramBotApplication.class);

    public static void main(String[] args) {
        // 在Spring上下文初始化之前，尝试获取环境变量
        String botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        String reviewGroupId = System.getenv("TELEGRAM_REVIEW_GROUP_ID");

        logger.info("Environment variable TELEGRAM_BOT_TOKEN: {}", botToken != null ? "******" : "NOT SET");
        logger.info("Environment variable TELEGRAM_REVIEW_GROUP_ID: {}", reviewGroupId);

        SpringApplication.run(ExampleTelegramBotApplication.class, args);
    }
}