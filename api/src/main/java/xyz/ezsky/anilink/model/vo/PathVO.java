package xyz.ezsky.anilink.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "服务端路径视图对象，用于展示文件/文件夹信息")
public class PathVO {
    @Schema(description = "名称（文件或文件夹名）", example = "anime")
    private String name;
    @Schema(description = "绝对路径", example = "/data/anime")
    private String path;
    @Schema(description = "类型（directory/file）", example = "directory")
    private String type;

    public PathVO(String name, String path, String type) {
        this.name = name;
        this.path = path;
        this.type = type;
    }
}
