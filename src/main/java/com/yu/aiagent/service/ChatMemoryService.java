package com.yu.aiagent.service;

import com.yu.aiagent.model.entity.ChatMemory;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yu.aiagent.model.vo.ManusConversationVO;
import com.yu.aiagent.model.vo.ManusMessageVO;
import com.yu.aiagent.model.vo.StockConversationVO;
import com.yu.aiagent.model.vo.StockMessageVO;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
* @author 86199
* @description 针对表【chat_memory(对话记忆)】的数据库操作Service
* @createDate 2026-05-16 16:05:13
*/
public interface ChatMemoryService extends IService<ChatMemory> {

    /**
     * 获取指定用户的 AI 股票大师历史会话列表。
     *
     * @param userId 用户 id
     * @return 会话摘要列表
     */
    List<StockConversationVO> listStockConversations(Long userId);

    /**
     * 根据关键词查询指定用户的 AI 股票大师历史会话列表。
     *
     * @param userId  用户 id
     * @param keyword 标题或内容关键词
     * @return 会话摘要列表
     */
    List<StockConversationVO> listStockConversations(Long userId, String keyword);

    /**
     * 获取指定 AI 股票大师会话的历史消息。
     *
     * @param conversationId 会话 id
     * @param userId         用户 id
     * @return 历史消息列表
     */
    List<StockMessageVO> getStockConversationMessages(String conversationId, Long userId);

    /**
     * 删除指定用户的 AI 股票大师会话。
     *
     * @param conversationId 会话 id
     * @param userId         用户 id
     * @return 是否删除成功
     */
    boolean deleteStockConversation(String conversationId, Long userId);

    /**
     * 重命名指定用户的 AI 股票大师会话
     *
     * @param conversationId 会话 id
     * @param userId         用户 id
     * @param title          新标题
     * @return 是否更新成功
     */
    boolean renameStockConversation(String conversationId, Long userId, String title);

    /**
     * 查询当前用户的 AI 超级智能体历史会话列表。
     *
     * @param userId 当前登录用户 id
     * @return AI 超级智能体会话摘要列表
     */
    List<ManusConversationVO> listManusConversations(Long userId);

    /**
     * 根据关键词查询当前用户的 AI 超级智能体历史会话列表。
     *
     * @param userId  当前登录用户 id
     * @param keyword 标题或内容关键词
     * @return AI 超级智能体会话摘要列表
     */
    List<ManusConversationVO> listManusConversations(Long userId, String keyword);

    /**
     * 查询某个 AI 超级智能体会话的历史消息，用于前端回显。
     *
     * @param conversationId 会话 id
     * @param userId         当前登录用户 id
     * @return 前端可直接展示的历史消息列表
     */
    List<ManusMessageVO> getManusConversationMessages(String conversationId, Long userId);

    /**
     * 软删除当前用户的某个 AI 超级智能体会话。
     *
     * @param conversationId 会话 id
     * @param userId         当前登录用户 id
     * @return 是否删除成功
     */
    boolean deleteManusConversation(String conversationId, Long userId);

    /**
     * 重命名指定用户的 AI 超级智能体会话
     *
     * @param conversationId 会话 id
     * @param userId         当前登录用户 id
     * @param title          新标题
     * @return 是否更新成功
     */
    boolean renameManusConversation(String conversationId, Long userId, String title);

    /**
     * 查询 AI 超级智能体运行时需要恢复的历史上下文。
     *
     * @param conversationId 会话 id
     * @param userId         当前登录用户 id
     * @return Spring AI 可识别的历史消息列表
     */
    List<Message> getManusMessagesForAgent(String conversationId, Long userId);

    /**
     * 保存 AI 超级智能体当前会话窗口中的可见消息。
     *
     * @param conversationId 会话 id
     * @param userId         当前登录用户 id
     * @param messages       需要持久化的消息列表
     */
    void saveManusMessages(String conversationId, Long userId, List<Message> messages);
}
