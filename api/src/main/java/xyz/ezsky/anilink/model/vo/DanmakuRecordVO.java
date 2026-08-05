package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 弹幕发送记录 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DanmakuRecordVO {

    private Long id;
    private Long userId;
    private String username;
    private Long episodeId;
    private Long animeId;
    private String animeTitle;

    /**
     * 封面图（关联 anime 表）
     */
    private String imageUrl;

    private Long videoId;
    private String episodeTitle;
    private Double time;
    private Integer mode;
    private Integer color;
    private String comment;
    private Long cid;
    private LocalDateTime createdAt;
}
