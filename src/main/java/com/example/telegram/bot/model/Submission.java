package com.example.telegram.bot.model;

import lombok.Data; // Lombok 注解，自动生成 getter/setter/toString/equals/hashCode
import java.util.ArrayList;
import java.util.List;

/**
 * 表示一个投稿任务的数据模型。
 */
@Data // 使用 Lombok 简化 POJO
public class Submission {
    private Long userId;
    private Long chatId; // 投稿所在的私聊chat ID
    private SubmissionState state;
    private String title;
    private String caption; // 媒体文件的说明文字
    private List<SubmissionContent> contents; // 支持多媒体内容

    public Submission(Long userId, Long chatId) {
        this.userId = userId;
        this.chatId = chatId;
        this.state = SubmissionState.INITIATED;
        this.contents = new ArrayList<>();
    }

    // 添加内容的方法
    public void addContent(String fileId, String fileType) {
        this.contents.add(new SubmissionContent(fileId, fileType));
    }

    // 内部类，表示投稿的单个内容项
    @Data
    public static class SubmissionContent {
        private String fileId;
        private String fileType; // e.g., "photo", "video", "audio", "document"

        public SubmissionContent(String fileId, String fileType) {
            this.fileId = fileId;
            this.fileType = fileType;
        }
    }
}