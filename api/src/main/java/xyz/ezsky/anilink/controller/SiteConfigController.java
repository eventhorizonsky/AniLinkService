package xyz.ezsky.anilink.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import cn.dev33.satoken.annotation.SaCheckRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import xyz.ezsky.anilink.model.dto.UpdateSiteConfigRequest;
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
    @SaCheckRole("super-admin")
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
        return ApiResponseVO.success("更新成功", "站点配置已更新");
    }
}
