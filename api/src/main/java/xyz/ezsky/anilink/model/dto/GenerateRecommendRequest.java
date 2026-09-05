package xyz.ezsky.anilink.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 生成「猜你喜欢」推荐请求体。
 */
@Data
public class GenerateRecommendRequest {

    /** 10 位维度开关（顺序同 BangumiRecommendService.REASONS，null=全开） */
    private List<Boolean> dimensions;
}
