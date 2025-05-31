// src/main/java/com/example/telegram/bot/KeyboardService.java
package com.example.telegram.bot.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责生成各种类型的Telegram机器人键盘（内联键盘和回复键盘）。
 */
@Service
public class KeyboardService {

    // --- 内联键盘 - 页面导航相关常量 ---
    public static final String ACTION_NEXT_PREFIX = "action:next";
    public static final String ACTION_PREV_PREFIX = "action:prev";
    public static final String ACTION_FIRST_PREFIX = "action:first";
    public static final String ACTION_LAST_PREFIX = "action:last";
    public static final String ACTION_CONFIG_PREFIX = "action:config"; // 配置按钮，无实际功能

    // 直接页面跳转回调 (如果需要，通常导航按钮就足够了)
    public static final String PAGE_1_CALLBACK = "page:1";
    public static final String PAGE_2_CALLBACK = "page:2";
    public static final String PAGE_3_CALLBACK = "page:3";

    // --- 内联键盘 - 审核相关常量 ---
    public static final String REVIEW_APPROVE_PREFIX = "review:approve";
    public static final String REVIEW_REJECT_PREFIX = "review:reject";

    /**
     * 根据页码创建对应的内联键盘（三级页面UI）。
     * 回调数据中包含当前页码，以便在处理时正确判断。
     * @param pageNum 当前页码。
     * @return 对应页面的 InlineKeyboardMarkup。
     */
    public InlineKeyboardMarkup createInlineKeyboardForPage(int pageNum) {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        // 添加顶部6个按钮 (3x2 布局)
        for (int i = 0; i < 2; i++) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            for (int j = 0; j < 3; j++) {
                // 按钮回调数据包含当前页码作为上下文 (例如："button:1-1")
                row.add(InlineKeyboardButton.builder()
                        .text("BUTTON " + (pageNum * 10 + i * 3 + j + 1)) // 示例按钮文本
                        .callbackData("button:" + pageNum + "-" + (i * 3 + j + 1)) // 示例回调数据
                        .build());
            }
            keyboardRows.add(row);
        }

        // 添加底部导航按钮
        List<InlineKeyboardButton> navRow = new ArrayList<>();
        if (pageNum == 1) {
            // 第一页：尾页 | 配置 | 下一页
            navRow.add(InlineKeyboardButton.builder().text("尾页").callbackData(ACTION_LAST_PREFIX + ":" + pageNum).build());
            navRow.add(InlineKeyboardButton.builder().text("配置").callbackData(ACTION_CONFIG_PREFIX + ":" + pageNum).build());
            navRow.add(InlineKeyboardButton.builder().text("下一页").callbackData(ACTION_NEXT_PREFIX + ":" + pageNum).build());
        } else if (pageNum == 2) {
            // 第二页：上一页 | 配置 | 下一页
            navRow.add(InlineKeyboardButton.builder().text("上一页").callbackData(ACTION_PREV_PREFIX + ":" + pageNum).build());
            navRow.add(InlineKeyboardButton.builder().text("配置").callbackData(ACTION_CONFIG_PREFIX + ":" + pageNum).build());
            navRow.add(InlineKeyboardButton.builder().text("下一页").callbackData(ACTION_NEXT_PREFIX + ":" + pageNum).build());
        } else if (pageNum == 3) {
            // 第三页：上一页 | 配置 | 首页
            navRow.add(InlineKeyboardButton.builder().text("上一页").callbackData(ACTION_PREV_PREFIX + ":" + pageNum).build());
            navRow.add(InlineKeyboardButton.builder().text("配置").callbackData(ACTION_CONFIG_PREFIX + ":" + pageNum).build());
            navRow.add(InlineKeyboardButton.builder().text("首页").callbackData(ACTION_FIRST_PREFIX + ":" + pageNum).build());
        }
        keyboardRows.add(navRow);

        markupInline.setKeyboard(keyboardRows);
        return markupInline;
    }

    /**
     * 创建输入框下方的回复键盘（主菜单功能键）。
     * @return ReplyKeyboardMarkup。
     */
    public ReplyKeyboardMarkup createReplyKeyboard() {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setResizeKeyboard(true); // 适应键盘大小
        replyKeyboardMarkup.setOneTimeKeyboard(false); // 保持键盘常驻，除非隐藏
        replyKeyboardMarkup.setSelective(false); // 对所有用户可见

        List<KeyboardRow> keyboard = new ArrayList<>();

        // 第一行按钮
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("🏠 首页")); // 文本内容将作为消息发送给机器人
        row1.add(new KeyboardButton("📦 我的订单"));
        row1.add(new KeyboardButton("💰 邀请返利"));
        keyboard.add(row1);

        replyKeyboardMarkup.setKeyboard(keyboard);
        return replyKeyboardMarkup;
    }

    /**
     * 创建用于投稿审核的内联键盘（通过/拒绝）。
     * 回调数据中包含投稿用户的ID，以便在处理时找到对应的投稿。
     * @param submissionUserId 投稿用户的ID。
     * @return 审核用的 InlineKeyboardMarkup。
     */
    public InlineKeyboardMarkup createReviewKeyboard(Long submissionUserId) {
        InlineKeyboardMarkup reviewMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();

        // 通过按钮：review:approve:投稿用户ID
        row.add(InlineKeyboardButton.builder()
                .text("✅ 通过")
                .callbackData(REVIEW_APPROVE_PREFIX + ":" + submissionUserId)
                .build());

        // 拒绝按钮：review:reject:投稿用户ID
        row.add(InlineKeyboardButton.builder()
                .text("❌ 拒绝")
                .callbackData(REVIEW_REJECT_PREFIX + ":" + submissionUserId)
                .build());

        keyboardRows.add(row);
        reviewMarkup.setKeyboard(keyboardRows);
        return reviewMarkup;
    }
}
