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
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.ResourceSearchVO;
import xyz.ezsky.anilink.service.ResourceDownloadService;
import xyz.ezsky.anilink.service.ResourceRssSubscriptionService;
import xyz.ezsky.anilink.service.ResourceSearchProxyService;

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
            @RequestParam(required = false) Integer type) {
        return ApiResponseVO.success(resourceSearchProxyService.fetchResources(keyword, subgroup, type));
    }

    @PostMapping("/download")
    @Operation(summary = "发起磁链下载任务")
    public ApiResponseVO<ResourceSearchVO.DownloadTask> createDownloadTask(@RequestBody ResourceSearchDownloadRequest request) {
        return ApiResponseVO.success(resourceDownloadService.startDownload(request), "下载任务已创建");
    }

    @GetMapping("/download-tasks")
    @Operation(summary = "查询下载任务列表")
    public ApiResponseVO<List<ResourceSearchVO.DownloadTask>> downloadTasks() {
        return ApiResponseVO.success(resourceDownloadService.listRecentTasks());
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
    @Operation(summary = "删除下载任务")
    public ApiResponseVO<Void> deleteTask(@PathVariable Long id) {
        resourceDownloadService.deleteTask(id);
        return ApiResponseVO.success(null, "任务已删除");
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
}
