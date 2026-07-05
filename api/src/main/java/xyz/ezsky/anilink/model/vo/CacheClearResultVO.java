package xyz.ezsky.anilink.model.vo;

import lombok.Data;

/**
 * 缓存清理结果视图对象
 */
@Data
public class CacheClearResultVO {

    /** 清理级别：expired / type / all */
    private String level;

    /** 清理的缓存类型（level=type 时有值） */
    private String cacheType;

    /** 删除的条数 */
    private int deletedCount;

    /** 清理前总条数 */
    private long beforeCount;

    /** 清理后剩余条数 */
    private long afterCount;

    public static CacheClearResultVO of(String level, String cacheType, int deletedCount, long beforeCount, long afterCount) {
        CacheClearResultVO vo = new CacheClearResultVO();
        vo.setLevel(level);
        vo.setCacheType(cacheType);
        vo.setDeletedCount(deletedCount);
        vo.setBeforeCount(beforeCount);
        vo.setAfterCount(afterCount);
        return vo;
    }
}
