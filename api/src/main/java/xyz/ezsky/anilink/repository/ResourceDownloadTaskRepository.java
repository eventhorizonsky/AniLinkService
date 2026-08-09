package xyz.ezsky.anilink.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import xyz.ezsky.anilink.model.entity.ResourceDownloadTask;
import xyz.ezsky.anilink.repository.base.BaseRepository;

import java.sql.Timestamp;
import java.util.List;

public interface ResourceDownloadTaskRepository extends BaseRepository<ResourceDownloadTask, Long> {
    List<ResourceDownloadTask> findTop100ByOrderByCreatedAtDesc();

    List<ResourceDownloadTask> findTop500ByStatusInOrderByCreatedAtDesc(List<ResourceDownloadTask.DownloadStatus> statuses);

    List<ResourceDownloadTask> findByStatusInOrderByCreatedAtAsc(List<ResourceDownloadTask.DownloadStatus> statuses);

    boolean existsByMagnet(String magnet);

    @Query("SELECT t FROM ResourceDownloadTask t " +
            "WHERE (:status IS NULL OR t.status = :status) " +
            "AND (:keyword IS NULL OR LOWER(t.title) LIKE CONCAT('%', LOWER(:keyword), '%')) " +
            "ORDER BY t.createdAt DESC")
    Page<ResourceDownloadTask> searchTasks(@Param("status") ResourceDownloadTask.DownloadStatus status,
                                           @Param("keyword") String keyword,
                                           Pageable pageable);

    @Query("SELECT t FROM ResourceDownloadTask t " +
            "WHERE t.status IN :statuses " +
            "AND (:keyword IS NULL OR LOWER(t.title) LIKE CONCAT('%', LOWER(:keyword), '%')) " +
            "ORDER BY t.createdAt DESC")
    Page<ResourceDownloadTask> searchTasksInStatuses(@Param("statuses") List<ResourceDownloadTask.DownloadStatus> statuses,
                                                     @Param("keyword") String keyword,
                                                     Pageable pageable);

    @Query("SELECT COUNT(t) FROM ResourceDownloadTask t " +
            "WHERE (:status IS NULL OR t.status = :status) " +
            "AND (:keyword IS NULL OR LOWER(t.title) LIKE CONCAT('%', LOWER(:keyword), '%'))")
    long countTasks(@Param("status") ResourceDownloadTask.DownloadStatus status,
                    @Param("keyword") String keyword);

    @Query("SELECT COUNT(t) FROM ResourceDownloadTask t " +
            "WHERE t.status IN :statuses " +
            "AND (:keyword IS NULL OR LOWER(t.title) LIKE CONCAT('%', LOWER(:keyword), '%'))")
    long countTasksInStatuses(@Param("statuses") List<ResourceDownloadTask.DownloadStatus> statuses,
                              @Param("keyword") String keyword);

    long countByStatus(ResourceDownloadTask.DownloadStatus status);

    @Query("SELECT COUNT(t) FROM ResourceDownloadTask t WHERE t.status = :status AND t.finishedAt >= :from")
    long countByStatusFinishedAfter(@Param("status") ResourceDownloadTask.DownloadStatus status,
                                    @Param("from") Timestamp from);
}
