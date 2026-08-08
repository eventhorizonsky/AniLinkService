package xyz.ezsky.anilink.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import xyz.ezsky.anilink.AniLinkServiceApplication;
import xyz.ezsky.anilink.model.entity.Anime;
import xyz.ezsky.anilink.model.entity.MediaFile;
import xyz.ezsky.anilink.model.entity.MediaLibrary;
import xyz.ezsky.anilink.repository.AnimeRepository;
import xyz.ezsky.anilink.repository.MediaFileRepository;
import xyz.ezsky.anilink.repository.MediaLibraryRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 动漫记录同步任务测试。
 *
 * <p>重点验证：在无外部事务的情况下（同步任务通过自调用执行清理逻辑），
 * 删除媒体库中无剧集的 Anime 记录仍能可靠执行，不会抛出
 * "No EntityManager with actual transaction available" 错误。</p>
 *
 * <p>使用独立的内存 H2 数据库，避免污染开发用的文件数据库。</p>
 */
@SpringBootTest(
        classes = AniLinkServiceApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:anilink-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.datasource.driver-class-name=org.h2.Driver"
        }
)
class AnimeSyncServiceTest {

    @Autowired
    private AnimeSyncService animeSyncService;

    @Autowired
    private AnimeRepository animeRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MediaLibraryRepository mediaLibraryRepository;

    @Test
    void cleanupAnimeWithoutEpisodes_removesOrphanAnimeWithoutOuterTransaction() {
        // 准备：媒体库 + 一部有剧集的动漫 + 一部无剧集的孤儿动漫
        MediaLibrary library = new MediaLibrary();
        library.setName("sync-test-lib");
        library.setPath("/tmp/sync-test");
        library = mediaLibraryRepository.save(library);

        Anime withEpisode = new Anime();
        withEpisode.setAnimeId(90001L);
        withEpisode.setTitle("有剧集动漫");
        animeRepository.save(withEpisode);

        Anime orphan = new Anime();
        orphan.setAnimeId(90002L);
        orphan.setTitle("无剧集孤儿动漫");
        animeRepository.save(orphan);

        MediaFile file = new MediaFile();
        file.setLibrary(library);
        file.setFilePath("/tmp/sync-test/ep1.mkv");
        file.setFileName("ep1.mkv");
        file.setLastModified(System.currentTimeMillis());
        file.setSize(100L);
        file.setAnimeId(90001L);
        mediaFileRepository.save(file);

        // 执行：模拟定时任务无外部事务的调用场景
        int deleted = animeSyncService.cleanupAnimeWithoutEpisodes();

        // 断言：孤儿动漫被删除，有剧集的动漫保留
        assertEquals(1, deleted, "应删除 1 条无剧集的 Anime 记录");
        assertTrue(animeRepository.findByAnimeId(90001L).isPresent(), "有剧集的动漫应保留");
        assertFalse(animeRepository.findByAnimeId(90002L).isPresent(), "无剧集的孤儿动漫应被删除");

        // 清理测试数据
        mediaFileRepository.delete(file);
        animeRepository.delete(animeRepository.findByAnimeId(90001L).get());
        mediaLibraryRepository.delete(library);
    }
}
