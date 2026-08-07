package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 版本发布视图对象（来自 GitHub Release）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseVO {

    /**
     * 版本标签（如 v1.0.0）
     */
    private String tagName;

    /**
     * 发布名称
     */
    private String name;

    /**
     * 发布时间
     */
    private String publishedAt;

    /**
     * GitHub Release 页面地址
     */
    private String htmlUrl;

    /**
     * 发布说明（markdown 格式 changelog）
     */
    private String body;

    /**
     * 是否为当前运行版本
     */
    private boolean current;
}
