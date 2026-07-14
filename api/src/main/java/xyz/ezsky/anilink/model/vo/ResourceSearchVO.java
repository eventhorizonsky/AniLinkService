package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.List;

public final class ResourceSearchVO {
    private ResourceSearchVO() {
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NamedItem {
        private Integer id;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceItem {
        private String title;
        private Integer typeId;
        private String typeName;
        private Integer subgroupId;
        private String subgroupName;
        private String magnet;
        private String pageUrl;
        private String fileSize;
        private String publishDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceListResult {
        private Boolean hasMore;
        private List<ResourceItem> resources;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DownloadTask {
        private Long id;
        private String title;
        private String magnet;
        private String pageUrl;
        private String fileSize;
        private String publishDate;
        private String subgroupName;
        private String typeName;
        private Long libraryId;
        private String libraryName;
        private String status;
        private Integer progressPercent;
        private Long downloadedBytes;
        private Long totalBytes;
        private String downloadSpeedText;
        private String uploadSpeedText;
        private String speedText;
        private String outputMessage;
        private String errorMessage;
        private String tempDir;
        private String finalPath;
        private Long mediaFileId;
        private Timestamp startedAt;
        private Timestamp finishedAt;
        private Timestamp createdAt;
        private Timestamp updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BindingStatus {
        private Long taskId;
        private String taskStatus;
        private String finalPath;
        private Long mediaFileId;
        private Boolean mediaFileExists;
        private Long animeId;
        private String animeTitle;
        private String episodeId;
        private String episodeTitle;
        private String matchStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RssSubscriptionItem {
        private Long id;
        private String name;
        private String feedUrl;
        private Long libraryId;
        private String libraryName;
        private Integer intervalMinutes;
        private Boolean enabled;
        private String includeFilter;
        private String excludeFilter;
        private String lastError;
        private Timestamp lastCheckedAt;
        private Timestamp lastSuccessAt;
        private Timestamp createdAt;
        private Timestamp updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RssFetchedContent {
        private Long id;
        private String name;
        private Timestamp lastCheckedAt;
        private String lastFetchedContent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RssFilterPreviewItem {
        private String title;
        private String link;
        private boolean included;
        private boolean excluded;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RssFilterPreviewResult {
        private Integer totalCount;
        private Integer includedCount;
        private Integer excludedCount;
        private String includeFilter;
        private String excludeFilter;
        private boolean includeFilterValid;
        private boolean excludeFilterValid;
        private String includeFilterError;
        private String excludeFilterError;
        private List<RssFilterPreviewItem> entries;
    }
}
