package xyz.ezsky.anilink.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "媒体库数据传输对象，用于创建新的媒体库")
public class MediaLibraryDTO {
    @Schema(description = "媒体库的名称", example = "我的动漫")
    private String name;
    @Schema(description = "媒体库的物理路径", example = "/data/anime")
    private String path;
}
