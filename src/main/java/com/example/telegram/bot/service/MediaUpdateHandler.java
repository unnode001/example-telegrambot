package com.example.telegram.bot.service;

import com.example.telegram.bot.model.Submission;
import com.example.telegram.bot.model.SubmissionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * 负责处理用户发送的多媒体内容（图片、视频、音频）。
 */
@Component
public class MediaUpdateHandler implements UpdateHandler {

    private static final Logger logger = LoggerFactory.getLogger(MediaUpdateHandler.class);

    private final SubmissionService submissionService;

    public MediaUpdateHandler(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @Override
    public boolean canHandle(Update update) {
        // 仅处理私聊中的多媒体消息
        return update.hasMessage() && update.getMessage().isUserMessage() &&
                (update.getMessage().hasPhoto() || update.getMessage().hasVideo() || update.getMessage().hasAudio() || update.getMessage().hasDocument());
    }

    @Override
    public void handle(Update update, AbsSender absSender) {
        Message message = update.getMessage();
        Long userId = message.getFrom().getId();
        Long chatId = message.getChatId();

        Submission currentSubmission = submissionService.getSubmission(userId);

        // 只有当有进行中的投稿任务，且处于等待内容或已接收内容状态时才处理媒体
        if (currentSubmission != null &&
                (currentSubmission.getState() == SubmissionState.INITIATED || currentSubmission.getState() == SubmissionState.CONTENT_RECEIVED)) {

            String fileId = null;
            String fileType = null;

            if (message.hasPhoto()) {
                // 获取最大尺寸的图片
                fileId = message.getPhoto().stream()
                        .max(java.util.Comparator.comparing(org.telegram.telegrambots.meta.api.objects.PhotoSize::getFileSize))
                        .map(org.telegram.telegrambots.meta.api.objects.PhotoSize::getFileId)
                        .orElse(null);
                fileType = "photo";
                currentSubmission.setCaption(message.getCaption()); // 保存图片说明
            } else if (message.hasVideo()) {
                fileId = message.getVideo().getFileId();
                fileType = "video";
                currentSubmission.setCaption(message.getCaption()); // 保存视频说明
            } else if (message.hasAudio()) {
                fileId = message.getAudio().getFileId();
                fileType = "audio";
                currentSubmission.setCaption(message.getCaption()); // 保存音频说明
            } else if (message.hasDocument()) {
                fileId = message.getDocument().getFileId();
                fileType = "document";
                currentSubmission.setCaption(message.getCaption()); // 保存文档说明
            }

            if (fileId != null) {
                currentSubmission.addContent(fileId, fileType);
                submissionService.updateSubmissionState(userId, SubmissionState.CONTENT_RECEIVED); // 标记为已收到内容

                sendBotMessage(absSender, chatId, "已收到您的 " + fileType + "。您可以继续发送其他内容，或发送 /done 来完成内容提交。");
                logger.info("Received {} from user {} for submission.", fileType, userId);
            } else {
                sendBotMessage(absSender, chatId, "无法识别您发送的多媒体文件。请发送图片、视频或音频。");
                logger.warn("Received unrecognized media type from user {}.", userId);
            }
        } else {
            // 如果没有进行中的投稿任务，或者状态不对，提示用户
            sendBotMessage(absSender, chatId, "请先发送 /submit 来开始一个新的投稿任务。");
            logger.info("Media received from user {} without active submission.", userId);
        }
    }

    private void sendBotMessage(AbsSender absSender, Long chatId, String text) {
        try {
            absSender.execute(new SendMessage(String.valueOf(chatId), text));
        } catch (TelegramApiException e) {
            logger.error("Failed to send message to chat ID {}: {}", chatId, e.getMessage(), e);
        }
    }
}
