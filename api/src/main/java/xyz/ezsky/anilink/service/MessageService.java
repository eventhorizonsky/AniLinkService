package xyz.ezsky.anilink.service;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.ezsky.anilink.model.entity.Message;
import xyz.ezsky.anilink.model.vo.MessageVO;
import xyz.ezsky.anilink.model.vo.PageVO;
import xyz.ezsky.anilink.repository.MessageRepository;
import xyz.ezsky.anilink.service.notification.NotificationSenderManager;
import lombok.extern.log4j.Log4j2;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 消息管理服务
 */
@Service
@Log4j2
public class MessageService {
    
    @Autowired
    private MessageRepository messageRepository;
    
    @Autowired
    private NotificationSenderManager notificationSenderManager;
    
    /**
     * 创建并发送消息
     */
    @Transactional
    public MessageVO createMessage(Long userId, String type, String title, String content, 
                                   Long animeId, String animeTitle, Long videoId, String episodeId) {
        Message message = new Message();
        message.setUserId(userId);
        message.setType(type);
        message.setTitle(title);
        message.setContent(content);
        message.setAnimeId(animeId);
        message.setAnimeTitle(animeTitle);
        message.setVideoId(videoId);
        message.setEpisodeId(episodeId);
        message.setIsRead(false);
        
        Message saved = messageRepository.save(message);
        
        // 发送通知（如果配置了通知发送者）
        MessageVO messageVO = convertToVO(saved);
        try {
            notificationSenderManager.sendNotification(userId, messageVO);
        } catch (Exception e) {
            log.error("Failed to send notification for message {}: {}", saved.getId(), e.getMessage());
        }
        
        log.info("Created message for user {} with type {}", userId, type);
        return messageVO;
    }
    
    /**
     * 获取用户的消息列表（分页）
     */
    public PageVO<MessageVO> getUserMessages(Long userId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<Message> messagePage = messageRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        
        List<MessageVO> data = messagePage.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        
        return PageVO.<MessageVO>builder()
                .content(data)
                .totalElements(messagePage.getTotalElements())
                .totalPages(messagePage.getTotalPages())
                .currentPage(page)
                .pageSize(pageSize)
                .build();
    }
    
    /**
     * 获取用户的未读消息
     */
    public List<MessageVO> getUserUnreadMessages(Long userId) {
        return messageRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取用户的所有消息
     */
    public List<MessageVO> getUserAllMessages(Long userId) {
        return messageRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取未读消息数
     */
    public long getUnreadCount(Long userId) {
        return messageRepository.countByUserIdAndIsReadFalse(userId);
    }
    
    /**
     * 获取用户指定剧集的未读消息数
     */
    public long getUnreadCountByEpisodeId(Long userId, String episodeId) {
        if (episodeId == null || episodeId.isBlank()) {
            return 0;
        }
        return messageRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId).stream()
                .filter(m -> episodeId.equals(m.getEpisodeId()))
                .count();
    }

    /**
     * 将用户指定剧集的全部未读消息标记为已读
     *
     * @return 已读数量
     */
    @Transactional
    public int markMessagesReadByEpisodeId(Long userId, String episodeId) {
        if (episodeId == null || episodeId.isBlank()) {
            return 0;
        }
        int count = messageRepository.markReadByEpisodeId(userId, episodeId, LocalDateTime.now());
        if (count > 0) {
            log.info("Marked {} messages as read for user {} episode {}", count, userId, episodeId);
        }
        return count;
    }

    /**
     * 标记消息为已读
     */
    @Transactional
    public MessageVO markMessageAsRead(Long messageId) {
        Optional<Message> messageOpt = messageRepository.findById(messageId);
        if (messageOpt.isPresent()) {
            Message message = messageOpt.get();
            message.setIsRead(true);
            message.setReadAt(LocalDateTime.now());
            Message saved = messageRepository.save(message);
            return convertToVO(saved);
        }
        return null;
    }
    
    /**
     * 标记所有消息为已读
     */
    @Transactional
    public void markAllMessagesAsRead(Long userId) {
        List<Message> unreadMessages = messageRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
        LocalDateTime now = LocalDateTime.now();
        for (Message message : unreadMessages) {
            message.setIsRead(true);
            message.setReadAt(now);
        }
        messageRepository.saveAll(unreadMessages);
        log.info("Marked all messages as read for user {}", userId);
    }
    
    /**
     * 删除消息
     */
    @Transactional
    public boolean deleteMessage(Long messageId, Long userId) {
        Optional<Message> messageOpt = messageRepository.findById(messageId);
        if (messageOpt.isPresent() && messageOpt.get().getUserId().equals(userId)) {
            messageRepository.delete(messageOpt.get());
            return true;
        }
        return false;
    }
    
    /**
     * 获取特定类型的消息
     */
    public PageVO<MessageVO> getMessagesByType(Long userId, String type, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        Page<Message> messagePage = messageRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, pageable);
        
        List<MessageVO> data = messagePage.getContent().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        
        return PageVO.<MessageVO>builder()
                .content(data)
                .totalElements(messagePage.getTotalElements())
                .totalPages(messagePage.getTotalPages())
                .currentPage(page)
                .pageSize(pageSize)
                .build();
    }
    
    /**
     * 为番剧新剧集发送通知（被外部调用，如爬虫服务）
     */
    @Transactional
    public void notifyNewEpisode(Long userId, Long animeId, String animeTitle, Long videoId, String episodeName) {
        // 创建新剧集更新消息
        MessageVO message = createMessage(userId, "episode_update", 
            "新剧集更新通知", 
            String.format("《%s》有新剧集发布: %s", animeTitle, episodeName),
            animeId, animeTitle, videoId, null);
        log.info("Sent episode update notification to user {} for anime {}", userId, animeId);
    }
    
    /**
     * 将实体转换为VO
     */
    private MessageVO convertToVO(Message message) {
        MessageVO vo = new MessageVO();
        BeanUtils.copyProperties(message, vo);
        return vo;
    }
}
