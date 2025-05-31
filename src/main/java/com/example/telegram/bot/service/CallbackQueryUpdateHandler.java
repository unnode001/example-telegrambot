package com.example.telegram.bot.service;

import com.example.telegram.bot.model.Submission;
import com.example.telegram.bot.model.SubmissionState;
import com.example.telegram.bot.service.SubmissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;

/**
 * 负责处理内联键盘回调查询，包括页面跳转和投稿审核。
 */
@Component
public class CallbackQueryUpdateHandler implements UpdateHandler {

    private static final Logger logger = LoggerFactory.getLogger(CallbackQueryUpdateHandler.class);

    private final KeyboardService keyboardService;
    private final SubmissionService submissionService;

    // 注入公共聊天群组ID
    @Value("${telegram.group.public.id}")
    private Long publicChatGroupId;

    public CallbackQueryUpdateHandler(KeyboardService keyboardService, SubmissionService submissionService) {
        this.keyboardService = keyboardService;
        this.submissionService = submissionService;
    }

    @Override
    public boolean canHandle(Update update) {
        return update.hasCallbackQuery();
    }

    @Override
    public void handle(Update update, AbsSender absSender) {
        CallbackQuery callbackQuery = update.getCallbackQuery();
        String callData = callbackQuery.getData();
        long messageId = callbackQuery.getMessage().getMessageId();
        long chatId = callbackQuery.getMessage().getChatId();

        logger.info("Received callback query from chat ID {} with data: {}", chatId, callData);

        // 处理审核相关的回调，使用 KeyboardService 中定义的常量
        if (callData.startsWith(KeyboardService.REVIEW_APPROVE_PREFIX) || callData.startsWith(KeyboardService.REVIEW_REJECT_PREFIX)) {
            handleReviewCallback(absSender, callbackQuery, callData, messageId, chatId);
            return; // 审核回调处理完毕，不再执行页面跳转逻辑
        }

        // --- 以下是原有的页面跳转逻辑 ---
        int currentPage = 1; // Default current page, will be updated based on callback data
        String[] dataParts = callData.split(":");

        // Safely parse current page from action callback's third part or button callback's second part
        if (dataParts.length > 2 && dataParts[0].equals("action")) {
            try {
                currentPage = Integer.parseInt(dataParts[2]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid current page number in action callback data: {}", callData);
            }
        } else if (dataParts.length > 1 && dataParts[0].equals("button") && dataParts[1].contains("-")) {
            try {
                currentPage = Integer.parseInt(dataParts[1].split("-")[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid page number in button callback data: {}", callData);
            }
        } else if (dataParts.length > 1 && dataParts[0].equals("page")) { // Direct page jump callback
            try {
                currentPage = Integer.parseInt(dataParts[1]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid page number in direct page callback data: {}", callData);
            }
        }

        int targetPage = currentPage; // Default target page is current page

        String actionIdentifier = dataParts[0];
        if (dataParts.length > 1 && (actionIdentifier.equals("action") || actionIdentifier.equals("button"))) {
            actionIdentifier += ":" + dataParts[1];
        }

        // Determine target page based on callback data
        switch (actionIdentifier) {
            case KeyboardService.ACTION_NEXT_PREFIX:
                targetPage = Math.min(currentPage + 1, 3); // Max 3 pages
                break;
            case KeyboardService.ACTION_PREV_PREFIX:
                targetPage = Math.max(currentPage - 1, 1); // Min 1 page
                break;
            case KeyboardService.ACTION_FIRST_PREFIX:
                targetPage = 1;
                break;
            case KeyboardService.ACTION_LAST_PREFIX:
                targetPage = 3; // Assuming 3 pages in total
                break;
            case KeyboardService.ACTION_CONFIG_PREFIX:
                logger.info("Config button clicked on page {}", currentPage);
                // Answer callback query to prevent loading spinner on button
                answerCallbackQuery(absSender, callbackQuery.getId(), "配置功能待实现", false);
                return; // Do not update keyboard/page, return directly
            case KeyboardService.PAGE_1_CALLBACK: // Direct page jump callback
                targetPage = 1;
                break;
            case KeyboardService.PAGE_2_CALLBACK:
                targetPage = 2;
                break;
            case KeyboardService.PAGE_3_CALLBACK:
                targetPage = 3;
                break;
            default:
                // Handle generic buttons (e.g., "button:X-Y")
                if (actionIdentifier.startsWith("button:")) {
                    logger.info("Generic button clicked: {}", callData);
                    // Answer callback query for generic buttons
                    answerCallbackQuery(absSender, callbackQuery.getId(), "你点击了 " + callbackQuery.getMessage().getText() + " 上的按钮", false);
                    // For generic buttons, stay on the same page by default. targetPage is already set to currentPage.
                } else {
                    logger.warn("Unknown callback data received: {}", callData);
                    answerCallbackQuery(absSender, callbackQuery.getId(), "未知操作", true); // Show alert for unknown
                    return; // Unknown callback, do nothing
                }
                break;
        }

        // Use EditMessageText to modify both message text and reply keyboard
        EditMessageText editMessageText = EditMessageText.builder()
                .chatId(String.valueOf(chatId))
                .messageId((int) messageId)
                .text("当前是第 " + targetPage + " 页") // Update text to reflect new page number
                .replyMarkup(keyboardService.createInlineKeyboardForPage(targetPage))
                .build();

        try {
            absSender.execute(editMessageText);
            logger.info("Updated message {} in chat {} to display page {}", messageId, chatId, targetPage);
        } catch (TelegramApiException e) {
            logger.error("Failed to edit message {} in chat {}: {}", messageId, chatId, e.getMessage(), e);
        }
    }

    /**
     * 处理审核相关的回调查询。
     * @param absSender AbsSender 实例
     * @param callbackQuery 回调查询对象
     * @param callData 回调数据
     * @param messageId 消息ID
     * @param chatId 聊天ID
     */
    private void handleReviewCallback(AbsSender absSender, CallbackQuery callbackQuery, String callData, long messageId, long chatId) {
        String[] parts = callData.split(":"); // e.g., "review:approve:12345"
        if (parts.length < 3) {
            logger.warn("Invalid review callback data: {}", callData);
            answerCallbackQuery(absSender, callbackQuery.getId(), "无效的审核操作", true);
            return;
        }

        String action = parts[1]; // "approve" or "reject"
        Long submissionUserId = Long.parseLong(parts[2]); // 投稿用户的ID

        Submission submission = submissionService.getSubmission(submissionUserId);

        if (submission == null || submission.getState() != SubmissionState.PENDING_REVIEW) {
            answerCallbackQuery(absSender, callbackQuery.getId(), "该投稿已处理或不存在。", true);
            // 尝试编辑消息，移除按钮
            editMessageText(absSender, chatId, messageId, callbackQuery.getMessage().getText() + "\n\n[已处理或无效]", null);
            return;
        }

        String statusMessage;
        if (KeyboardService.REVIEW_APPROVE_PREFIX.split(":")[1].equals(action)) { // 使用常量进行比较
            submissionService.updateSubmissionState(submissionUserId, SubmissionState.APPROVED);
            statusMessage = "投稿已通过！";
            // 发布到公共群组
            publishSubmissionToPublicChat(absSender, submission);
            // 通知投稿用户
            sendBotMessage(absSender, submission.getChatId(), "恭喜！您的投稿《" + submission.getTitle() + "》已通过审核，并已发布到公共群组。");
        } else if (KeyboardService.REVIEW_REJECT_PREFIX.split(":")[1].equals(action)) { // 使用常量进行比较
            submissionService.updateSubmissionState(submissionUserId, SubmissionState.REJECTED);
            statusMessage = "投稿已拒绝。";
            // 通知投稿用户
            sendBotMessage(absSender, submission.getChatId(), "很抱歉，您的投稿《" + submission.getTitle() + "》未通过审核。");
        } else {
            logger.warn("Unknown review action: {}", action);
            answerCallbackQuery(absSender, callbackQuery.getId(), "未知审核操作", true);
            return;
        }

        // 回答回调查询，防止按钮一直转圈
        answerCallbackQuery(absSender, callbackQuery.getId(), statusMessage, false);

        // 编辑审核消息，显示结果并移除按钮
        editMessageText(absSender, chatId, messageId, callbackQuery.getMessage().getText() + "\n\n" + statusMessage, null);
        logger.info("Submission from user {} was {} by admin in chat {}", submissionUserId, action, chatId);
    }

    /**
     * 将投稿内容发布到公共聊天群组。
     * @param absSender AbsSender 实例
     * @param submission 投稿对象
     */
    private void publishSubmissionToPublicChat(AbsSender absSender, Submission submission) {
        String caption = "✨ 新投稿 ✨\n\n" +
                "标题: " + submission.getTitle() + "\n" +
                (submission.getCaption() != null ? "说明: " + submission.getCaption() + "\n" : "") +
                "\n#投稿 #分享"; // 可以添加话题标签

        // 如果有媒体内容，发送媒体文件
        if (submission.getContents() != null && !submission.getContents().isEmpty()) {
            Submission.SubmissionContent firstContent = submission.getContents().get(0); // 假设只发布第一个媒体
            InputFile inputFile = new InputFile(firstContent.getFileId());

            try {
                switch (firstContent.getFileType()) {
                    case "photo":
                        SendPhoto sendPhoto = new SendPhoto(String.valueOf(publicChatGroupId), inputFile);
                        sendPhoto.setCaption(caption);
                        absSender.execute(sendPhoto);
                        break;
                    case "video":
                        SendVideo sendVideo = new SendVideo(String.valueOf(publicChatGroupId), inputFile);
                        sendVideo.setCaption(caption);
                        absSender.execute(sendVideo);
                        break;
                    case "audio":
                        SendAudio sendAudio = new SendAudio(String.valueOf(publicChatGroupId), inputFile);
                        sendAudio.setCaption(caption);
                        absSender.execute(sendAudio);
                        break;
                    case "document":
                        SendDocument sendDocument = new SendDocument(String.valueOf(publicChatGroupId), inputFile);
                        sendDocument.setCaption(caption);
                        absSender.execute(sendDocument);
                        break;
                    default:
                        SendMessage sendMessage = new SendMessage(String.valueOf(publicChatGroupId), caption);
                        absSender.execute(sendMessage);
                        break;
                }
                logger.info("Published submission from user {} to public chat group {}", submission.getUserId(), publicChatGroupId);
            } catch (TelegramApiException e) {
                logger.error("Failed to publish media submission to public chat group {}: {}", publicChatGroupId, e.getMessage(), e);
            }
        } else {
            // 如果没有媒体内容，只发送文本消息
            SendMessage sendMessage = new SendMessage(String.valueOf(publicChatGroupId), caption);
            try {
                absSender.execute(sendMessage);
                logger.info("Published text submission from user {} to public chat group {}", submission.getUserId(), publicChatGroupId);
            } catch (TelegramApiException e) {
                logger.error("Failed to publish text submission to public chat group {}: {}", publicChatGroupId, e.getMessage(), e);
            }
        }
    }

    private void sendBotMessage(AbsSender absSender, Long chatId, String text) {
        try {
            absSender.execute(new SendMessage(String.valueOf(chatId), text));
        } catch (TelegramApiException e) {
            logger.error("Failed to send message to chat ID {}: {}", chatId, e.getMessage(), e);
        }
    }

    private void editMessageText(AbsSender absSender, Long chatId, long messageId, String newText, InlineKeyboardMarkup replyMarkup) {
        EditMessageText editMessageText = EditMessageText.builder()
                .chatId(String.valueOf(chatId))
                .messageId((int) messageId)
                .text(newText)
                .replyMarkup(replyMarkup)
                .build();
        try {
            absSender.execute(editMessageText);
        } catch (TelegramApiException e) {
            logger.error("Failed to edit message {}: {}", messageId, e.getMessage(), e);
        }
    }

    private void answerCallbackQuery(AbsSender absSender, String callbackQueryId, String text, boolean showAlert) {
        try {
            absSender.execute(AnswerCallbackQuery.builder()
                    .callbackQueryId(callbackQueryId)
                    .text(text)
                    .showAlert(showAlert)
                    .build());
        } catch (TelegramApiException e) {
            logger.error("Failed to answer callback query {}: {}", callbackQueryId, e.getMessage(), e);
        }
    }
}
