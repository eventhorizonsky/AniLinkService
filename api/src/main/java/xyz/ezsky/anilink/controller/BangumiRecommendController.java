package xyz.ezsky.anilink.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.ezsky.anilink.model.dto.GenerateRecommendRequest;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.service.BangumiRecommendService;

import java.util.Map;

/**
 * Bangumi「猜你喜欢」全站个性化推荐接口。
 *
 * <p>需登录且已绑定 Bangumi 账号。每次生成均实时重新拉取用户收藏（分状态分页）与
 * 各条目官方推荐（next API /p1/subjects/{id}/recs），按 10 个可开关维度加权评分，
 * 结果落库供页面展示与主动重新生成。</p>
 */
@Tag(name = "Bangumi 猜你喜欢", description = "基于用户 Bangumi 收藏的全站个性化推荐（需绑定账号）")
@RestController
@RequestMapping("/api/bangumi/recommend")
@Log4j2
public class BangumiRecommendController {

    @Autowired
    private BangumiRecommendService bangumiRecommendService;

    @SaCheckLogin
    @Operation(summary = "推荐页状态", description = "返回绑定状态、生成任务进度与已落库结果摘要")
    @GetMapping("/status")
    public ApiResponseVO<Map<String, Object>> getStatus() {
        try {
            return ApiResponseVO.success(bangumiRecommendService.getStatus(currentUserId()));
        } catch (Exception e) {
            log.error("获取猜你喜欢状态失败", e);
            return ApiResponseVO.fail(500, "状态获取失败：" + e.getMessage());
        }
    }

    @SaCheckLogin
    @Operation(summary = "生成推荐", description = "实时拉取收藏与条目推荐并按维度开关计算，异步执行，进度见 /status")
    @PostMapping("/generate")
    public ApiResponseVO<Map<String, Object>> generate(@RequestBody(required = false) GenerateRecommendRequest request) {
        try {
            return ApiResponseVO.success(bangumiRecommendService.startGenerate(
                    currentUserId(), request == null ? null : request.getDimensions()));
        } catch (BangumiRecommendService.BangumiRecommendException e) {
            return ApiResponseVO.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("启动猜你喜欢生成失败", e);
            return ApiResponseVO.fail(500, "生成任务启动失败：" + e.getMessage());
        }
    }

    @SaCheckLogin
    @Operation(summary = "推荐结果", description = "读取上次生成的完整推荐列表（含推荐分与推荐理由）")
    @GetMapping("/result")
    public ApiResponseVO<Map<String, Object>> getResult() {
        try {
            return ApiResponseVO.success(bangumiRecommendService.getResult(currentUserId()));
        } catch (BangumiRecommendService.BangumiRecommendException e) {
            return ApiResponseVO.fail(404, e.getMessage());
        } catch (Exception e) {
            log.error("读取猜你喜欢结果失败", e);
            return ApiResponseVO.fail(500, "结果读取失败：" + e.getMessage());
        }
    }

    private Long currentUserId() {
        return Long.valueOf(StpUtil.getLoginId().toString());
    }
}
