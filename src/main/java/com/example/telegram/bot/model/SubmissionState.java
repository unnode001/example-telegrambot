package com.example.telegram.bot.model;

/**
 * 定义投稿任务在不同阶段的状态。
 */
public enum SubmissionState {
    NONE,                   // 没有进行中的投稿任务
    INITIATED,              // 投稿任务已启动，等待用户发送内容
    CONTENT_RECEIVED,       // 已收到内容（图片、视频、音频），等待用户发送标题
    TITLE_AWAITING,         // 标题已收到，等待用户确认
    CONFIRMATION_AWAITING,  // 等待用户确认提交
    PENDING_REVIEW,         // 内容已提交，等待管理员审核
    APPROVED,               // 投稿已通过审核
    REJECTED                // 投稿已被拒绝
}