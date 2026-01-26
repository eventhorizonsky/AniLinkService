package xyz.ezsky.anilink.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import xyz.ezsky.anilink.model.dto.SetSiteConfigRequest;
import xyz.ezsky.anilink.model.vo.SiteConfigVO;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
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
     * 设置站点配置和管理员账号
     * 用于安装引导
     */
    @PostMapping("config")
    @Operation(summary = "设置站点配置", description = "设置站点信息和管理员账号密码，用于安装引导")
    public ApiResponseVO<String> setSiteConfig(@RequestBody SetSiteConfigRequest request) {
        siteConfigService.setSiteConfig(request);
        return ApiResponseVO.success("设置成功", "站点配置已更新");
    }
}
