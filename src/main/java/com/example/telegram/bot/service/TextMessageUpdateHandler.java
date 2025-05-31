package com.example.telegram.bot.service;

import com.example.telegram.bot.model.Submission;
import com.example.telegram.bot.model.SubmissionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责处理文本消息，包括启动投稿流程、接收投稿标题和处理 /done 命令。
 */
@Component
public class TextMessageUpdateHandler implements UpdateHandler {

    private static final Logger logger = LoggerFactory.getLogger(TextMessageUpdateHandler.class);

    private final KeyboardService keyboardService;
    private final SubmissionService submissionService;

    // 注入审核群组ID
    @Value("${telegram.group.review.id}")
    private Long reviewGroupId;

    public TextMessageUpdateHandler(KeyboardService keyboardService, SubmissionService submissionService) {
        this.keyboardService = keyboardService;
        this.submissionService = submissionService;
    }

    @Override
    public boolean canHandle(Update update) {
        // 仅处理私聊中的文本消息
        return update.hasMessage() && update.getMessage().hasText() && update.getMessage().isUserMessage();
    }

    @Override
    public void handle(Update update, AbsSender absSender) {
        Message message = update.getMessage();
        String messageText = message.getText();
        Long userId = message.getFrom().getId();
        Long chatId = message.getChatId();

        logger.info("Received text message from user {} in chat {}: {}", userId, chatId, messageText);

        Submission currentSubmission = submissionService.getSubmission(userId);

        if (messageText.equals("/start") || messageText.equals("/menu")) {
            handleStartOrMenuCommand(absSender, chatId);
        } else if (messageText.equals("/submit")) {
            handleSubmissionInitiation(absSender, userId, chatId);
        } else if (messageText.equals("/done")) {
            // 处理 /done 命令，进入等待标题状态
            handleDoneCommand(absSender, userId, chatId, currentSubmission);
        } else if (messageText.equals("/cancel")) {
            // 处理 /cancel 命令，取消投稿
            handleCancelCommand(absSender, userId, chatId, currentSubmission);
        } else if (currentSubmission != null && currentSubmission.getState() == SubmissionState.CONTENT_RECEIVED) {
            // 如果当前在等待标题状态，则接收标题
            handleTitleInput(absSender, userId, chatId, messageText, currentSubmission);
        } else if (currentSubmission != null && currentSubmission.getState() == SubmissionState.CONFIRMATION_AWAITING) {
            // 如果在等待确认状态，处理确认/取消
            handleConfirmation(absSender, userId, chatId, messageText, currentSubmission, absSender);
        } else {
            handleOtherTextMessage(absSender, chatId, messageText);
        }
    }

    private void handleStartOrMenuCommand(AbsSender absSender, Long chatId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText("欢迎回来！这是您的主菜单。您可以发送 /submit 开始投稿。");
        sendMessage.setReplyMarkup(keyboardService.createReplyKeyboard()); // 使用 KeyboardService 创建回复键盘

        try {
            absSender.execute(sendMessage);
            logger.info("Sent welcome message with reply keyboard to chat ID {}", chatId);
        } catch (TelegramApiException e) {
            logger.error("Failed to send welcome message to chat ID {}: {}", chatId, e.getMessage(), e);
        }
    }

    private void handleSubmissionInitiation(AbsSender absSender, Long userId, Long chatId) {
        Submission submission = submissionService.getSubmission(userId);
        if (submission != null && submission.getState() != SubmissionState.NONE && submission.getState() != SubmissionState.REJECTED && submission.getState() != SubmissionState.APPROVED) {
            sendBotMessage(absSender, chatId, "您已经在进行一个投稿任务，请先完成或发送 /cancel 取消。");
            return;
        }

        submissionService.startNewSubmission(userId, chatId);
        sendBotMessage(absSender, chatId, "好的，请发送您的投稿内容（图片、视频、音频或文本）。完成后请发送 /done。");
        logger.info("Started new submission for user {}", userId);
    }

    private void handleDoneCommand(AbsSender absSender, Long userId, Long chatId, Submission submission) {
        if (submission == null || submission.getState() == SubmissionState.NONE) {
            sendBotMessage(absSender, chatId, "您当前没有进行中的投稿任务。请发送 /submit 开始。");
            return;
        }

        if (submission.getContents().isEmpty() && (submission.getTitle() == null || submission.getTitle().isEmpty())) {
            sendBotMessage(absSender, chatId, "您还没有发送任何投稿内容或标题。请先发送内容。");
            return;
        }

        // 此时，用户已经发送了内容，现在等待标题
        submissionService.updateSubmissionState(userId, SubmissionState.CONTENT_RECEIVED); // 确保状态正确
        sendBotMessage(absSender, chatId, "内容已接收。请发送您的投稿标题。");
        logger.info("User {} sent /done. Awaiting title.", userId);
    }

    private void handleCancelCommand(AbsSender absSender, Long userId, Long chatId, Submission submission) {
        if (submission != null && submission.getState() != SubmissionState.NONE) {
            submissionService.clearSubmission(userId);
            sendBotMessage(absSender, chatId, "投稿已取消。");
            logger.info("Submission cancelled by user {}.", userId);
        } else {
            sendBotMessage(absSender, chatId, "您当前没有进行中的投稿任务可以取消。");
        }
    }

    private void handleTitleInput(AbsSender absSender, Long userId, Long chatId, String titleText, Submission submission) {
        submission.setTitle(titleText);
        // 确保标题已设置，并更新状态到等待确认
        submissionService.updateSubmissionState(userId, SubmissionState.CONFIRMATION_AWAITING);

        StringBuilder confirmationMessage = new StringBuilder("您已提交内容和标题。\n\n");
        confirmationMessage.append("标题: ").append(submission.getTitle()).append("\n");
        confirmationMessage.append("内容类型: ");
        if (submission.getContents() != null && !submission.getContents().isEmpty()) {
            submission.getContents().forEach(content -> confirmationMessage.append(content.getFileType()).append(" "));
        } else {
            confirmationMessage.append("文本"); // If no media content, assume text submission
        }
        confirmationMessage.append("\n\n请确认提交吗？(发送 '是' 或 '否')");

        sendBotMessage(absSender, chatId, confirmationMessage.toString());
        logger.info("Received title for submission from user {}. Awaiting confirmation.", userId);
    }

    private void handleConfirmation(AbsSender absSender, Long userId, Long chatId, String confirmationText, Submission submission, AbsSender botSender) {
        if (confirmationText.equalsIgnoreCase("是")) {
            submissionService.updateSubmissionState(userId, SubmissionState.PENDING_REVIEW);
            sendBotMessage(absSender, chatId, "投稿已提交，等待审核。感谢您的投稿！");
            logger.info("Submission confirmed by user {}. Status: PENDING_REVIEW.", userId);

            // 发送投稿到审核群组，使用 KeyboardService 创建审核按钮
            sendSubmissionToReviewGroup(botSender, submission);

        } else if (confirmationText.equalsIgnoreCase("否")) {
            submissionService.clearSubmission(userId);
            sendBotMessage(absSender, chatId, "投稿已取消。");
            logger.info("Submission cancelled by user {}.", userId);
        } else {
            sendBotMessage(absSender, chatId, "请发送 '是' 或 '否' 来确认您的投稿。");
        }
    }

    private void handleOtherTextMessage(AbsSender absSender, Long chatId, String messageText) {
        SendMessage echoMessage = new SendMessage();
        echoMessage.setChatId(String.valueOf(chatId));
        echoMessage.setText("你说了: " + messageText + "\n发送 /submit 开始投稿，或 /start 查看主菜单。");
        echoMessage.setReplyMarkup(keyboardService.createReplyKeyboard());
        try {
            absSender.execute(echoMessage);
            logger.info("Sent echo message to chat ID {}: {}", chatId, echoMessage.getText());
        } catch (TelegramApiException e) {
            logger.error("Failed to send echo message to chat ID {}: {}", chatId, e.getMessage(), e);
        }
    }

    private void sendBotMessage(AbsSender absSender, Long chatId, String text) {
        try {
            absSender.execute(new SendMessage(String.valueOf(chatId), text));
        } catch (TelegramApiException e) {
            logger.error("Failed to send message to chat ID {}: {}", chatId, e.getMessage(), e);
        }
    }

    /**
     * 将投稿内容发送到审核群组。
     * @param absSender AbsSender 实例
     * @param submission 投稿对象
     */
    private void sendSubmissionToReviewGroup(AbsSender absSender, Submission submission) {
        String caption = "来自用户 " + submission.getUserId() + " 的投稿 (ID: " + submission.getUserId() + ")\n" +
                "标题: " + submission.getTitle() + "\n" +
                "说明: " + (submission.getCaption() != null ? submission.getCaption() : "无") + "\n\n" +
                "请审核：";

        // 使用 KeyboardService 创建审核按钮
        InlineKeyboardMarkup reviewMarkup = keyboardService.createReviewKeyboard(submission.getUserId());

        // 如果有媒体内容，发送媒体文件
        if (submission.getContents() != null && !submission.getContents().isEmpty()) {
            // 假设只发送第一个媒体文件作为示例，实际可能需要处理多个
            Submission.SubmissionContent firstContent = submission.getContents().get(0);
            InputFile inputFile = new InputFile(firstContent.getFileId());

            try {
                switch (firstContent.getFileType()) {
                    case "photo":
                        SendPhoto sendPhoto = new SendPhoto(String.valueOf(reviewGroupId), inputFile);
                        sendPhoto.setCaption(caption);
                        sendPhoto.setReplyMarkup(reviewMarkup);
                        absSender.execute(sendPhoto);
                        break;
                    case "video":
                        SendVideo sendVideo = new SendVideo(String.valueOf(reviewGroupId), inputFile);
                        sendVideo.setCaption(caption);
                        sendVideo.setReplyMarkup(reviewMarkup);
                        absSender.execute(sendVideo);
                        break;
                    case "audio":
                        SendAudio sendAudio = new SendAudio(String.valueOf(reviewGroupId), inputFile);
                        sendAudio.setCaption(caption);
                        sendAudio.setReplyMarkup(reviewMarkup);
                        absSender.execute(sendAudio);
                        break;
                    case "document":
                        SendDocument sendDocument = new SendDocument(String.valueOf(reviewGroupId), inputFile);
                        sendDocument.setCaption(caption);
                        sendDocument.setReplyMarkup(reviewMarkup);
                        absSender.execute(sendDocument);
                        break;
                    default:
                        // Fallback for unsupported media types or if no media
                        SendMessage sendMessage = new SendMessage(String.valueOf(reviewGroupId), caption);
                        sendMessage.setReplyMarkup(reviewMarkup);
                        absSender.execute(sendMessage);
                        break;
                }
                logger.info("Sent submission from user {} to review group {}", submission.getUserId(), reviewGroupId);
            } catch (TelegramApiException e) {
                logger.error("Failed to send media submission to review group {}: {}", reviewGroupId, e.getMessage(), e);
            }
        } else {
            // 如果没有媒体内容，只发送文本消息
            SendMessage sendMessage = new SendMessage(String.valueOf(reviewGroupId), caption);
            sendMessage.setReplyMarkup(reviewMarkup);
            try {
                absSender.execute(sendMessage);
                logger.info("Sent text submission from user {} to review group {}", submission.getUserId(), reviewGroupId);
            } catch (TelegramApiException e) {
                logger.error("Failed to send text submission to review group {}: {}", reviewGroupId, e.getMessage(), e);
            }
        }
    }
}
