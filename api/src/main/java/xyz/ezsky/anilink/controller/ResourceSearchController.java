package xyz.ezsky.anilink.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xyz.ezsky.anilink.model.dto.ResourceRssSubscriptionRequest;
import xyz.ezsky.anilink.model.dto.RssFilterPreviewRequest;
import xyz.ezsky.anilink.model.dto.ResourceSearchDownloadRequest;
import xyz.ezsky.anilink.model.dto.ResourceSearchBatchDownloadRequest;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.CombinedTrackerListVO;
import xyz.ezsky.anilink.model.vo.ResourceSearchVO;
import xyz.ezsky.anilink.model.vo.TrackerListStatusVO;
import xyz.ezsky.anilink.service.ResourceDownloadService;
import xyz.ezsky.anilink.service.ResourceRssSubscriptionService;
import xyz.ezsky.anilink.service.ResourceSearchProxyService;
import xyz.ezsky.anilink.service.TrackerListService;

import java.util.List;

@RestController
@RequestMapping("/api/resource-search")
@Tag(name = "资源搜索与下载", description = "弹弹节点代理搜索与下载任务管理")
@SaCheckRole("super-admin")
public class ResourceSearchController {

    @Autowired
    private ResourceSearchProxyService resourceSearchProxyService;

    @Autowired
    private ResourceDownloadService resourceDownloadService;

    @Autowired
    private ResourceRssSubscriptionService rssSubscriptionService;

    @Autowired
    private TrackerListService trackerListService;

    @GetMapping("/subgroup")
    @Operation(summary = "获取字幕组列表")
    public ApiResponseVO<List<ResourceSearchVO.NamedItem>> subgroup() {
        return ApiResponseVO.success(resourceSearchProxyService.fetchSubgroups());
    }

    @GetMapping("/type")
    @Operation(summary = "获取资源类型列表")
    public ApiResponseVO<List<ResourceSearchVO.NamedItem>> type() {
        return ApiResponseVO.success(resourceSearchProxyService.fetchTypes());
    }

    @GetMapping("/list")
    @Operation(summary = "搜索资源")
    public ApiResponseVO<ResourceSearchVO.ResourceListResult> list(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer subgroup,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false, defaultValue = "0") Integer offset) {
        return ApiResponseVO.success(resourceSearchProxyService.fetchResources(keyword, subgroup, type, offset));
    }

    @PostMapping("/download")
    @Operation(summary = "发起磁链下载任务")
    public ApiResponseVO<ResourceSearchVO.DownloadTask> createDownloadTask(@RequestBody ResourceSearchDownloadRequest request) {
        return ApiResponseVO.success(resourceDownloadService.startDownload(request), "下载任务已创建");
    }

    @PostMapping("/download/batch")
    @Operation(summary = "批量发起磁链下载任务")
    public ApiResponseVO<ResourceSearchVO.BatchDownloadResult> createDownloadTasks(@RequestBody ResourceSearchBatchDownloadRequest request) {
        return ApiResponseVO.success(resourceDownloadService.startDownloadBatch(request), "批量下载已提交");
    }

    @GetMapping("/download-tasks")
    @Operation(summary = "查询下载任务列表（分页 + 过滤 + 统计）")
    public ApiResponseVO<ResourceSearchVO.DownloadTaskPageResult> downloadTasks(
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return ApiResponseVO.success(resourceDownloadService.listTasks(page, size, status, keyword));
    }

    @GetMapping(value = "/download-tasks/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "下载任务进度 SSE")
    public SseEmitter streamDownloadTasks() {
        return resourceDownloadService.subscribeTaskProgress();
    }

    @PostMapping("/download-tasks/{id}/cancel")
    @Operation(summary = "取消下载任务")
    public ApiResponseVO<ResourceSearchVO.DownloadTask> cancelTask(@PathVariable Long id) {
        return ApiResponseVO.success(resourceDownloadService.cancelTask(id), "任务已取消");
    }

    @PostMapping("/download-tasks/{id}/retry")
    @Operation(summary = "重试下载任务")
    public ApiResponseVO<ResourceSearchVO.DownloadTask> retryTask(@PathVariable Long id) {
        return ApiResponseVO.success(resourceDownloadService.retryTask(id), "已创建重试任务");
    }

    @DeleteMapping("/download-tasks/{id}")
    @Operation(summary = "删除下载任务（同步清理暂存文件，媒体库文件不受影响）")
    public ApiResponseVO<Void> deleteTask(@PathVariable Long id) {
        resourceDownloadService.deleteTask(id);
        return ApiResponseVO.success(null, "任务已删除");
    }

    @PostMapping("/test-connection")
    @Operation(summary = "测试资源节点连接")
    public ApiResponseVO<ResourceSearchVO.NodeConnectionTestResult> testConnection() {
        return ApiResponseVO.success(resourceSearchProxyService.testConnection());
    }

    @GetMapping("/download-tasks/{id}/binding")
    @Operation(summary = "查询下载任务绑定状态")
    public ApiResponseVO<ResourceSearchVO.BindingStatus> bindingStatus(@PathVariable Long id) {
        return ApiResponseVO.success(resourceDownloadService.getBindingStatus(id));
    }

    @GetMapping("/rss-subscriptions")
    @Operation(summary = "查询 RSS 订阅列表")
    public ApiResponseVO<List<ResourceSearchVO.RssSubscriptionItem>> listRssSubscriptions() {
        return ApiResponseVO.success(rssSubscriptionService.listSubscriptions());
    }

    @PostMapping("/rss-subscriptions")
    @Operation(summary = "创建 RSS 订阅")
    public ApiResponseVO<ResourceSearchVO.RssSubscriptionItem> createRssSubscription(@RequestBody ResourceRssSubscriptionRequest request) {
        return ApiResponseVO.success(rssSubscriptionService.createSubscription(request), "RSS 订阅已创建");
    }

    @PutMapping("/rss-subscriptions/{id}")
    @Operation(summary = "更新 RSS 订阅")
    public ApiResponseVO<ResourceSearchVO.RssSubscriptionItem> updateRssSubscription(@PathVariable Long id,
                                                                                     @RequestBody ResourceRssSubscriptionRequest request) {
        return ApiResponseVO.success(rssSubscriptionService.updateSubscription(id, request), "RSS 订阅已更新");
    }

    @DeleteMapping("/rss-subscriptions/{id}")
    @Operation(summary = "删除 RSS 订阅")
    public ApiResponseVO<Void> deleteRssSubscription(@PathVariable Long id) {
        rssSubscriptionService.deleteSubscription(id);
        return ApiResponseVO.success(null, "RSS 订阅已删除");
    }

    @PostMapping("/rss-subscriptions/{id}/trigger")
    @Operation(summary = "立即检查 RSS 订阅")
    public ApiResponseVO<Void> triggerRssSubscription(@PathVariable Long id) {
        rssSubscriptionService.triggerNow(id);
        return ApiResponseVO.success(null, "已触发 RSS 检查");
    }

    @GetMapping("/rss-subscriptions/{id}/last-content")
    @Operation(summary = "查询 RSS 订阅最近拉取内容")
    public ApiResponseVO<ResourceSearchVO.RssFetchedContent> getRssLastContent(@PathVariable Long id) {
        return ApiResponseVO.success(rssSubscriptionService.getLastFetchedContent(id));
    }

    @PostMapping("/rss-subscriptions/preview")
    @Operation(summary = "预览 RSS 正则过滤结果")
    public ApiResponseVO<ResourceSearchVO.RssFilterPreviewResult> previewRssFilter(@RequestBody RssFilterPreviewRequest request) {
        return ApiResponseVO.success(rssSubscriptionService.previewFilter(request));
    }

    @GetMapping("/tracker-list/status")
    @Operation(summary = "查询 Tracker 列表订阅状态")
    public ApiResponseVO<TrackerListStatusVO> trackerListStatus() {
        return ApiResponseVO.success(trackerListService.getStatus());
    }

    @PostMapping("/tracker-list/refresh")
    @Operation(summary = "立即刷新 Tracker 列表订阅")
    public ApiResponseVO<TrackerListStatusVO> refreshTrackerList() {
        return ApiResponseVO.success(trackerListService.refreshNow(), "已触发 Tracker 列表刷新");
    }

    @GetMapping("/tracker-list/combined")
    @Operation(summary = "查看最终 Tracker 列表", description = "返回自定义 Tracker 与订阅 Tracker 合并去重后的最终列表")
    public ApiResponseVO<CombinedTrackerListVO> combinedTrackerList() {
        return ApiResponseVO.success(resourceDownloadService.getCombinedTrackerList());
    }
}
