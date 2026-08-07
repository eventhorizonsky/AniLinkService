package xyz.ezsky.anilink.model.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 版本信息视图对象
 */
@Data
@NoArgsConstructor
public class VersionInfoVO {

    /**
     * 当前运行版本
     */
    private String currentVersion;

    /**
     * GitHub 仓库（owner/repo）
     */
    private String repo;

    /**
     * GitHub Release 列表（已标记 current）
     */
    private List<ReleaseVO> releases;

    public VersionInfoVO(String currentVersion, String repo, List<ReleaseVO> releases) {
        this.currentVersion = currentVersion;
        this.repo = repo;
        this.releases = releases;
    }
}
