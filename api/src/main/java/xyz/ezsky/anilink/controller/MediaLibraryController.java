package xyz.ezsky.anilink.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import cn.dev33.satoken.annotation.SaCheckRole;
import xyz.ezsky.anilink.model.dto.MediaLibraryDTO;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;
import xyz.ezsky.anilink.model.vo.MediaLibraryVO;
import xyz.ezsky.anilink.model.vo.PathVO;
import xyz.ezsky.anilink.service.MediaLibraryService;

import java.util.List;

@Tag(name = "媒体库管理", description = "用于管理媒体库")
@RestController
@RequestMapping("/api/media-library")
public class MediaLibraryController {

    @Autowired
    private MediaLibraryService mediaLibraryService;

    @Operation(summary = "添加媒体库", description = "添加一个新的媒体库，并立即触发一次扫描")
    @PostMapping
    public ApiResponseVO<MediaLibraryVO> addLibrary(@RequestBody MediaLibraryDTO mediaLibraryDTO) {
        return ApiResponseVO.success(mediaLibraryService.addLibrary(mediaLibraryDTO));
    }

    @Operation(summary = "获取所有媒体库", description = "查询并返回系统中所有已配置的媒体库")
    @SaCheckRole("super-admin")
    @GetMapping
    public ApiResponseVO<List<MediaLibraryVO>> getAllLibraries() {
        return ApiResponseVO.success(mediaLibraryService.findAll());
    }

    @Operation(summary = "删除媒体库", description = "根据ID删除指定的媒体库")
    @SaCheckRole("super-admin")
    @DeleteMapping("/{id}")
    public ApiResponseVO<Void> deleteLibrary(@Parameter(description = "要删除的媒体库ID") @PathVariable Long id) {
        mediaLibraryService.deleteLibrary(id);
        return ApiResponseVO.success(null, "删除成功");
    }

    @Operation(summary = "扫描指定媒体库", description = "根据ID手动触发一次指定媒体库的扫描")
    @SaCheckRole("super-admin")
    @PostMapping("/scan/{id}")
    public ApiResponseVO<Void> scanLibrary(@Parameter(description = "要扫描的媒体库ID") @PathVariable Long id) {
        mediaLibraryService.scanLibrary(id);
        return ApiResponseVO.success(null, "扫描已触发");
    }

    @Operation(summary = "扫描所有媒体库", description = "手动触发一次对所有媒体库的扫描")
    @SaCheckRole("super-admin")
    @PostMapping("/scan-all")
    public ApiResponseVO<Void> scanAllLibraries() {
        mediaLibraryService.scanAllLibraries();
        return ApiResponseVO.success(null, "扫描已触发");
    }

    @Operation(summary = "查询服务端路径", description = "查询指定根目录下的所有文件和文件夹路径，支持筛选")
    @SaCheckRole("super-admin")
    @GetMapping("/paths")
    public ApiResponseVO<List<PathVO>> listServerPaths(
            @RequestParam String rootPath,
            @RequestParam(required = false, defaultValue = "false") boolean onlyDir,
            @RequestParam(required = false) String fileType) {
        return ApiResponseVO.success(mediaLibraryService.listServerPaths(rootPath, onlyDir, fileType));
    }
}
