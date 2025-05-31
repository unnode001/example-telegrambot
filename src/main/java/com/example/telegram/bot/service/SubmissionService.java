package com.example.telegram.bot.service;

import com.example.telegram.bot.model.Submission;
import com.example.telegram.bot.model.SubmissionState;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 负责管理投稿任务的状态和数据。
 * 注意：此处使用 ConcurrentHashMap 存储，应用重启数据会丢失。
 * 生产环境应使用数据库持久化。
 */
@Service
public class SubmissionService {

    // 存储每个用户当前正在进行的投稿任务
    // Key: userId, Value: Submission object
    private final Map<Long, Submission> userSubmissions = new ConcurrentHashMap<>();

    /**
     * 获取指定用户当前正在进行的投稿任务。
     * @param userId 用户ID
     * @return 投稿任务对象，如果不存在则为 null
     */
    public Submission getSubmission(Long userId) {
        return userSubmissions.get(userId);
    }

    /**
     * 开始一个新的投稿任务。
     * @param userId 用户ID
     * @param chatId 投稿所在的私聊chat ID
     * @return 新创建的投稿任务对象
     */
    public Submission startNewSubmission(Long userId, Long chatId) {
        Submission submission = new Submission(userId, chatId);
        userSubmissions.put(userId, submission);
        return submission;
    }

    /**
     * 更新投稿任务的状态。
     * @param userId 用户ID
     * @param newState 新状态
     */
    public void updateSubmissionState(Long userId, SubmissionState newState) {
        Submission submission = userSubmissions.get(userId);
        if (submission != null) {
            submission.setState(newState);
        }
    }

    /**
     * 清除指定用户的投稿任务。
     * @param userId 用户ID
     */
    public void clearSubmission(Long userId) {
        userSubmissions.remove(userId);
    }
}