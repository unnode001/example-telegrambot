package com.example.telegram.bot.service;

import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;

/**
 * 定义处理不同类型Telegram更新的通用接口。
 */
public interface UpdateHandler {

    /**
     * 检查此处理器是否可以处理给定的更新。
     * @param update 要处理的更新。
     * @return 如果可以处理则为 true，否则为 false。
     */
    boolean canHandle(Update update);

    /**
     * 处理给定的更新。
     * @param update 要处理的更新。
     * @param absSender 用于执行Telegram API方法的AbsSender实例。
     */
    void handle(Update update, AbsSender absSender);
}