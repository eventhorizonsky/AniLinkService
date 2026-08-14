package xyz.ezsky.anilink.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import xyz.ezsky.anilink.model.dto.TestEmailRequest;
import xyz.ezsky.anilink.model.dto.UpdateSiteConfigRequest;
import xyz.ezsky.anilink.model.vo.SiteConfigVO;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.service.EmailService;
import xyz.ezsky.anilink.service.ResourceDownloadService;
import xyz.ezsky.anilink.service.SiteConfigService;

/**
 * 站点配置控制器
 */
@RestController
@RequestMapping("/api/site/")
@Tag(name = "站点配置", description = "站点配置相关接口")
public class SiteConfigController {
    
    @Autowired
    private SiteConfigService siteConfigService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ResourceDownloadService resourceDownloadService;
    
    /**
     * 获取站点配置信息
     */
    @GetMapping("config")
    @Operation(summary = "获取站点配置", description = "获取站点名称、描述、URL等配置信息")
    public ApiResponseVO<SiteConfigVO> getSiteConfig() {
        SiteConfigVO config = siteConfigService.getSiteConfig();
        return ApiResponseVO.success(config, "获取站点配置成功");
    }
    
    /**
     * 更新站点配置
     */
    @PutMapping("config")
    @SaCheckRole("super-admin")
    @Operation(summary = "更新站点配置", description = "更新站点名称、描述、URL等配置信息")
    public ApiResponseVO<String> updateSiteConfig(@RequestBody UpdateSiteConfigRequest request) {
        siteConfigService.updateSiteConfig(request);
        if (request.getResourceDownloadLimitKbps() != null || request.getResourceUploadLimitKbps() != null) {
            resourceDownloadService.refreshGlobalRateLimit();
        }
        return ApiResponseVO.success("更新成功", "站点配置已更新");
    }

    /**
     * 测试 SMTP 邮件发送
     */
    @PostMapping("test-email")
    @SaCheckRole("super-admin")
    @Operation(summary = "发送测试邮件", description = "使用当前 SMTP 配置发送测试邮件")
    public ApiResponseVO<String> sendTestEmail(@RequestBody TestEmailRequest request) {
        if (request.getToEmail() == null || request.getToEmail().isBlank()) {
            return ApiResponseVO.fail(400, "请填写收件邮箱");
        }
        emailService.sendTestEmail(request.getToEmail());
        return ApiResponseVO.success("ok", "测试邮件发送成功");
    }
}
