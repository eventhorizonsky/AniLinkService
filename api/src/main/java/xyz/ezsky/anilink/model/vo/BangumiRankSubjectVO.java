package xyz.ezsky.anilink.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bangumi 动画排行榜条目 VO
 */
@Data
@NoArgsConstructor
@Schema(description = "Bangumi 动画排行榜条目")
public class BangumiRankSubjectVO {

    @Schema(description = "Bangumi subject ID（bgmtvSubjectId）", example = "876")
    private Long subjectId;

    @Schema(description = "中文名", example = "CLANNAD 〜AFTER STORY〜")
    private String nameCn;

    @Schema(description = "原名", example = "CLANNAD 〜AFTER STORY〜")
    private String name;

    @Schema(description = "封面图地址")
    private String cover;

    @Schema(description = "播出年月（YYYY-MM-01，来自条目信息）", example = "2008-10-01")
    private String date;

    @Schema(description = "Bangumi 排名（0 表示未上榜）", example = "1")
    private Integer rank;

    @Schema(description = "评分", example = "9.2")
    private Double score;

    @Schema(description = "评分人数", example = "31608")
    private Integer votes;
}
