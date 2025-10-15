package xyz.ezsky.anilink.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xyz.ezsky.anilink.model.dto.MediaLibraryDTO;
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
    public MediaLibraryVO addLibrary(@RequestBody MediaLibraryDTO mediaLibraryDTO) {
        return mediaLibraryService.addLibrary(mediaLibraryDTO);
    }

    @Operation(summary = "获取所有媒体库", description = "查询并返回系统中所有已配置的媒体库")
    @GetMapping
    public List<MediaLibraryVO> getAllLibraries() {
        return mediaLibraryService.findAll();
    }

    @Operation(summary = "删除媒体库", description = "根据ID删除指定的媒体库")
    @DeleteMapping("/{id}")
    public void deleteLibrary(@Parameter(description = "要删除的媒体库ID") @PathVariable Long id) {
        mediaLibraryService.deleteLibrary(id);
    }

    @Operation(summary = "扫描指定媒体库", description = "根据ID手动触发一次指定媒体库的扫描")
    @PostMapping("/scan/{id}")
    public void scanLibrary(@Parameter(description = "要扫描的媒体库ID") @PathVariable Long id) {
        mediaLibraryService.scanLibrary(id);
    }

    @Operation(summary = "扫描所有媒体库", description = "手动触发一次对所有媒体库的扫描")
    @PostMapping("/scan-all")
    public void scanAllLibraries() {
        mediaLibraryService.scanAllLibraries();
    }

    @Operation(summary = "查询服务端路径", description = "查询指定根目录下的所有文件和文件夹路径，支持筛选")
    @GetMapping("/paths")
    public List<PathVO> listServerPaths(
            @RequestParam String rootPath,
            @RequestParam(required = false, defaultValue = "false") boolean onlyDir,
            @RequestParam(required = false) String fileType) {
        return mediaLibraryService.listServerPaths(rootPath, onlyDir, fileType);
    }
}
