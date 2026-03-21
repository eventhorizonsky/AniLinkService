package xyz.ezsky.anilink.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新播放进度请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayHistoryDTO {
    
    private Long videoId;
    
    private String videoName;
    
    private Long animeId;
    
    private String animeTitle;

    private String episodeId;
    
    /**
     * 播放进度（秒）
     */
    private Long progressSeconds;
    
    /**
     * 视频总时长（秒）
     */
    private Long durationSeconds;
    
    /**
     * 是否已完成（当progressSeconds >= durationSeconds时为true）
     */
    private Boolean isCompleted;
}
