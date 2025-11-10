package xyz.ezsky.anilink.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import xyz.ezsky.anilink.model.entity.MediaLibrary;

@Data
@Schema(description = "媒体库视图对象，用于展示媒体库信息")
public class MediaLibraryVO {
    @Schema(description = "媒体库的唯一标识符")
    private Long id;
    @Schema(description = "媒体库的名称", example = "我的动漫")
    private String name;
    @Schema(description = "媒体库的物理路径", example = "/data/anime")
    private String path;
    @Schema(description = "媒体库当前状态", example = "OK")
    private MediaLibrary.Status status;
}
