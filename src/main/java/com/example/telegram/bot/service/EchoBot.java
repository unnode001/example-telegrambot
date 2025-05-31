package com.example.telegram.bot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

/**
 * 回声机器人主类，负责接收更新并委托给相应的处理器。
 * 继承 TelegramLongPollingBot 以处理长轮询更新。
 */
@Component
public class EchoBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(EchoBot.class);

    @Value("${telegram.bot.name}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    // 注入所有 UpdateHandler 实现类
    private final List<UpdateHandler> updateHandlers;

    // Spring 会自动收集所有实现 UpdateHandler 接口的 Bean 并注入到这个列表中
    public EchoBot(List<UpdateHandler> updateHandlers) {
        this.updateHandlers = updateHandlers;
    }

    /**
     * 当接收到新的更新时调用此方法。
     * @param update 接收到的更新对象。
     */
    @Override
    public void onUpdateReceived(Update update) {
        // 遍历所有处理器，找到能处理当前更新的处理器并执行
        for (UpdateHandler handler : updateHandlers) {
            if (handler.canHandle(update)) {
                handler.handle(update, this); // 'this' refers to the EchoBot (AbsSender) instance
                return; // Once handled, exit
            }
        }
        logger.warn("No handler found for update: {}", update);
    }

    /**
     * 返回机器人的用户名。
     * @return 机器人的用户名。
     */
    @Override
    public String getBotUsername() {
        return botUsername;
    }

    /**
     * 返回机器人的令牌。
     * @return 机器人的令牌。
     */
    @Override
    public String getBotToken() {
        return botToken;
    }
}