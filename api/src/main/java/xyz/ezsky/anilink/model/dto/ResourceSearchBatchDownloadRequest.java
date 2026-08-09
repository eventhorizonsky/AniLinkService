package xyz.ezsky.anilink.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量下载请求")
public class ResourceSearchBatchDownloadRequest {

    @Schema(description = "目标媒体库 ID")
    private Long libraryId;

    @Schema(description = "待下载资源列表")
    private List<ResourceSearchDownloadRequest> items;
}
