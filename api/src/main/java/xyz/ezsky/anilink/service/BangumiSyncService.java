package xyz.ezsky.anilink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.ezsky.anilink.model.entity.Anime;
import xyz.ezsky.anilink.model.entity.AnimeFollow;
import xyz.ezsky.anilink.model.entity.User;
import xyz.ezsky.anilink.repository.AnimeFollowRepository;
import xyz.ezsky.anilink.repository.AnimeRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Bangumi 自动同步编排服务。
 * <p>
 * 所有同步均为最大努力（best-effort）、异步执行、失败静默。
 * 绝不因为 Bangumi API 不可用或用户未绑定而影响本地功能。
 * </p>
 */
@Service
@Log4j2
public class BangumiSyncService {

    private static final int BANGUMI_TYPE_WISH = 1;    // 想看
    private static final int BANGUMI_TYPE_DONE = 2;    // 看过
    private static final int BANGUMI_TYPE_DOING = 3;   // 在看
    private static final int BANGUMI_TYPE_ON_HOLD = 4; // 搁置
    private static final int BANGUMI_TYPE_DROPPED = 5; // 抛弃

    private static final int EPISODE_TYPE_WATCHED = 2; // 看过

    @Autowired
    private BangumiApiService bangumiApiService;

    @Autowired
    private AnimeRepository animeRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private AnimeFollowRepository animeFollowRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 异步执行线程池，2 个核心线程，最大 4 线程，空闲 60 秒回收。
     */
    private final ExecutorService syncExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "bangumi-sync");
        t.setDaemon(true);
        return t;
    });

    /**
     * 剧集 ID 映射缓存。key = "subjectId:episodeNumber"，value = Bangumi episode ID。
     * 缓存 1 小时后过期。
     */
    private final ConcurrentHashMap<String, CachedEpisodeId> episodeIdCache = new ConcurrentHashMap<>();

    private static class CachedEpisodeId {
        final Long episodeId;
        final long createdAt;

        CachedEpisodeId(Long episodeId) {
            this.episodeId = episodeId;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > 3600_000; // 1 小时
        }
    }

    // ==================== 公开方法 ====================

    /**
     * 将追番状态变更同步到 Bangumi。
     * 异步执行，失败静默。
     *
     * @param userId      本地用户 ID
     * @param animeId     本地番剧 ID（弹弹 animeId）
     * @param localStatus 本地追番状态（watching/completed/dropped）
     */
    public void syncFollowStatusToBangumi(Long userId, Long animeId, String localStatus) {
        CompletableFuture.runAsync(() -> {
            try {
                doSyncFollowStatus(userId, animeId, localStatus);
            } catch (Exception e) {
                log.warn("Bangumi sync failed: follow status for userId={}, animeId={}, status={}: {}",
                        userId, animeId, localStatus, e.getMessage());
            }
        }, syncExecutor);
    }

    /**
     * 本地放弃追番时同步到 Bangumi。
     * <p>
     * 官方公开/私人 API（dist.json、openapi.json）均未提供删除收藏的操作，
     * 因此降级为将 Bangumi 收藏标记为"抛弃"（type=5）。
     * 异步执行，失败静默。
     *
     * @param userId           本地用户 ID
     * @param animeId          本地番剧 ID（可为 null，用于兜底反查 subjectId）
     * @param bangumiSubjectId 追番记录上的 Bangumi subjectId（可为 null，会通过 animeId 反查）
     */
    public void syncUnfollowToBangumi(Long userId, Long animeId, Long bangumiSubjectId) {
        CompletableFuture.runAsync(() -> {
            try {
                doSyncUnfollow(userId, animeId, bangumiSubjectId);
            } catch (Exception e) {
                log.warn("Bangumi sync failed: unfollow for userId={}, animeId={}, subjectId={}: {}",
                        userId, animeId, bangumiSubjectId, e.getMessage());
            }
        }, syncExecutor);
    }

    /**
     * 将单集"已看"状态同步到 Bangumi。
     * 异步执行，失败静默。
     *
     * @param userId        本地用户 ID
     * @param animeId       本地番剧 ID
     * @param episodeNumber 集数（如 "5"）
     */
    public void syncEpisodeWatchedToBangumi(Long userId, Long animeId, String episodeNumber) {
        CompletableFuture.runAsync(() -> {
            try {
                doSyncEpisodeWatched(userId, animeId, episodeNumber);
            } catch (Exception e) {
                log.warn("Bangumi sync failed: episode watched for userId={}, animeId={}, episodeNumber={}: {}",
                        userId, animeId, episodeNumber, e.getMessage());
            }
        }, syncExecutor);
    }

    /**
     * 从 Bangumi 拉取用户的所有动画收藏，同步到本地追番。
     * 以 Bangumi 数据为准：同一 subjectId 如果本地已有记录则覆盖状态。
     *
     * @param userId 本地用户 ID
     * @return 同步结果摘要 Map
     */
    public Map<String, Object> pullBangumiCollections(Long userId) {
        Map<String, Object> result = new LinkedHashMap<>();
        int total = 0, created = 0, updated = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        // 1. 验证 Bangumi 账号绑定
        String accessToken = getBangumiAccessToken(userId);
        if (accessToken == null) {
            result.put("success", false);
            result.put("message", "未绑定 Bangumi 账号");
            return result;
        }
        Optional<User> userOpt = userService.findById(userId);
        String username = userOpt.map(User::getBangumiUsername).orElse(null);
        if (!StringUtils.hasText(username)) {
            result.put("success", false);
            result.put("message", "Bangumi 账号信息不完整");
            return result;
        }

        // 2. 预加载本地番剧库的 subjectId -> animeId 映射。
        //    同步时只做本地匹配，不再批量调用弹弹接口，避免触发风控。
        Map<Long, Long> subjectToAnimeId = new HashMap<>();
        for (Anime anime : animeRepository.findAll()) {
            if (anime.getBangumiSubjectId() != null) {
                subjectToAnimeId.putIfAbsent(anime.getBangumiSubjectId(), anime.getAnimeId());
            }
        }

        // 3. 分页拉取 Bangumi 动画收藏（subject_type=2）
        int offset = 0;
        int limit = 50;
        try {
            while (true) {
                ResponseEntity<String> response = bangumiApiService.getUserCollections(
                        accessToken, username, 2, null, limit, offset);
                if (!response.getStatusCode().is2xxSuccessful()) {
                    result.put("success", false);
                    result.put("message", "Bangumi API 返回错误: HTTP " + response.getStatusCode().value());
                    return result;
                }
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode data = root.get("data");
                if (data == null || !data.isArray() || data.size() == 0) break;
                total += data.size();

                for (JsonNode item : data) {
                    try {
                        Long subjectId = item.get("subject_id").asLong();
                        int bgmType = item.get("type").asInt(3);
                        String localStatus = mapBangumiTypeToLocalStatus(bgmType);

                        // 从 Bangumi 返回中提取标题和封面
                        String title = null;
                        String imageUrl = null;
                        if (item.has("subject")) {
                            JsonNode subject = item.get("subject");
                            title = subject.has("name_cn") && !subject.get("name_cn").isNull()
                                    ? subject.get("name_cn").asText()
                                    : subject.has("name") ? subject.get("name").asText() : null;
                            if (subject.has("images") && subject.get("images").has("common")) {
                                imageUrl = subject.get("images").get("common").asText();
                            }
                        }

                        // 仅按本地番剧库匹配；查不到则保持未绑定，由用户点击时再触发查询
                        Long localAnimeId = subjectToAnimeId.get(subjectId);

                        // 查找已有记录：优先按 subjectId 匹配，其次按 animeId
                        Optional<AnimeFollow> existing = Optional.empty();
                        if (localAnimeId != null) {
                            existing = animeFollowRepository.findByUserIdAndAnimeId(userId, localAnimeId);
                        }
                        if (existing.isEmpty()) {
                            // 按 bangumi_subject_id 查找
                            List<AnimeFollow> allFollows = animeFollowRepository.findByUserIdOrderByUpdatedAtDesc(userId);
                            existing = allFollows.stream()
                                    .filter(f -> subjectId.equals(f.getBangumiSubjectId()))
                                    .findFirst();
                        }

                        if (existing.isPresent()) {
                            AnimeFollow follow = existing.get();
                            follow.setStatus(localStatus);
                            follow.setBangumiSubjectId(subjectId);
                            if (localAnimeId != null) follow.setAnimeId(localAnimeId);
                            if (title != null && (follow.getAnimeTitle() == null || follow.getAnimeTitle().isBlank())) {
                                follow.setAnimeTitle(title);
                            }
                            // 封面以 Bangumi 返回为准（比弹弹的清晰），同步时直接覆盖
                            if (imageUrl != null && !imageUrl.isBlank()) {
                                follow.setImageUrl(imageUrl);
                            }
                            follow.setUpdatedAt(LocalDateTime.now());
                            animeFollowRepository.save(follow);
                            updated++;
                        } else {
                            // 未匹配也入库
                            AnimeFollow follow = new AnimeFollow();
                            follow.setUserId(userId);
                            follow.setAnimeId(localAnimeId);
                            follow.setBangumiSubjectId(subjectId);
                            follow.setAnimeTitle(title != null ? title : "未知番剧");
                            follow.setImageUrl(imageUrl);
                            follow.setStatus(localStatus);
                            animeFollowRepository.save(follow);
                            created++;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to sync Bangumi collection item: subjectId={}",
                                item.has("subject_id") ? item.get("subject_id").asText() : "?", e);
                        errors.add("处理收藏条目失败: " + e.getMessage());
                        skipped++;
                    }
                }

                // 检查是否还有下一页
                if (data.size() < limit) break;
                offset += limit;
            }
        } catch (Exception e) {
            log.error("Failed to pull Bangumi collections for userId={}", userId, e);
            result.put("success", false);
            result.put("message", "拉取 Bangumi 收藏失败: " + e.getMessage());
            return result;
        }

        // 兜底：清理历史污染数据（animeId / subjectId 重复的追番记录）
        int mergedDuplicates = 0;
        try {
            mergedDuplicates = mergeDuplicateFollows(userId);
        } catch (Exception e) {
            log.warn("Failed to merge duplicate follows for userId={}: {}", userId, e.getMessage());
        }

        result.put("success", true);
        result.put("total", total);
        result.put("created", created);
        result.put("updated", updated);
        result.put("skipped", skipped);
        result.put("mergedDuplicates", mergedDuplicates);
        if (!errors.isEmpty()) result.put("errors", errors);
        return result;
    }

    /**
     * 兜底清理：合并当前用户重复的追番记录。
     * 主要处理上线前因接口异常、后续再匹配等产生的污染数据：
     * 1) animeId 相同的多条记录合并为一条；
     * 2) bangumiSubjectId 相同（含 animeId 为空）的残留记录合并。
     * 在同步完成后调用，同时供追番查询路径调用，实现查询即自愈。
     *
     * @return 合并时删除的重复记录条数
     */
    public int mergeDuplicateFollows(Long userId) {
        int removed = 0;

        // 1. 按 animeId 分组去重
        Map<Long, List<AnimeFollow>> byAnimeId = animeFollowRepository
                .findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(f -> f.getAnimeId() != null)
                .collect(Collectors.groupingBy(AnimeFollow::getAnimeId));
        for (List<AnimeFollow> group : byAnimeId.values()) {
            if (group.size() > 1) {
                removed += mergeFollowGroup(group);
            }
        }

        // 2. 按 bangumiSubjectId 分组去重（覆盖 animeId 为空但 subjectId 相同的残留记录）
        Map<Long, List<AnimeFollow>> bySubjectId = animeFollowRepository
                .findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(f -> f.getBangumiSubjectId() != null)
                .collect(Collectors.groupingBy(AnimeFollow::getBangumiSubjectId));
        for (List<AnimeFollow> group : bySubjectId.values()) {
            if (group.size() > 1) {
                removed += mergeFollowGroup(group);
            }
        }
        return removed;
    }

    /**
     * 合并一组重复追番：保留最完整的一条，其余删除并把缺失字段合并到保留记录上。
     *
     * @return 删除的记录条数
     */
    private int mergeFollowGroup(List<AnimeFollow> group) {
        // 保留优先级：有 subjectId > 有 animeId > updatedAt 更新 > id 更小
        Comparator<AnimeFollow> comparator = Comparator
                .comparing((AnimeFollow f) -> f.getBangumiSubjectId() != null)
                .thenComparing(f -> f.getAnimeId() != null)
                .thenComparing(AnimeFollow::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(AnimeFollow::getId, Comparator.reverseOrder());
        AnimeFollow primary = group.stream().max(comparator).orElse(null);
        if (primary == null) return 0;

        int removed = 0;
        for (AnimeFollow other : group) {
            if (other.getId().equals(primary.getId())) continue;
            mergeFollowFields(primary, other);
            animeFollowRepository.delete(other);
            removed++;
        }
        if (removed > 0) {
            primary.setUpdatedAt(LocalDateTime.now());
            animeFollowRepository.save(primary);
            log.info("Merged {} duplicate follow records into followId={} for userId={}",
                    removed, primary.getId(), primary.getUserId());
        }
        return removed;
    }

    /**
     * 将 other 的字段合并到 primary（仅补充 primary 缺失的字段）。
     */
    private void mergeFollowFields(AnimeFollow primary, AnimeFollow other) {
        if (primary.getAnimeId() == null) primary.setAnimeId(other.getAnimeId());
        if (primary.getBangumiSubjectId() == null) primary.setBangumiSubjectId(other.getBangumiSubjectId());
        if (!StringUtils.hasText(primary.getAnimeTitle()) && StringUtils.hasText(other.getAnimeTitle())) {
            primary.setAnimeTitle(other.getAnimeTitle());
        }
        if (!StringUtils.hasText(primary.getImageUrl()) && StringUtils.hasText(other.getImageUrl())) {
            primary.setImageUrl(other.getImageUrl());
        }
        if (!StringUtils.hasText(primary.getTags()) && StringUtils.hasText(other.getTags())) {
            primary.setTags(other.getTags());
        }
        if (primary.getFollowAt() == null
                || (other.getFollowAt() != null && other.getFollowAt().isBefore(primary.getFollowAt()))) {
            primary.setFollowAt(other.getFollowAt());
        }
    }

    /**
     * 将 Bangumi 收藏类型映射为本地追番状态。
     */
    private String mapBangumiTypeToLocalStatus(int bgmType) {
        return switch (bgmType) {
            case 1 -> "wish";
            case 2 -> "watched";
            case 3 -> "watching";
            case 4 -> "on_hold";
            case 5 -> "dropped";
            default -> "wish";
        };
    }

    // ==================== 私有实现 ====================

    /**
     * 同步追番状态到 Bangumi 收藏。
     */
    private void doSyncFollowStatus(Long userId, Long animeId, String localStatus) {
        // 1. 获取用户 access token
        String accessToken = getBangumiAccessToken(userId);
        if (accessToken == null) {
            log.debug("Bangumi sync skipped: no access token for userId={}", userId);
            return;
        }

        // 2. 获取 Bangumi subject ID
        Long subjectId = getBangumiSubjectId(animeId);
        if (subjectId == null) {
            log.debug("Bangumi sync skipped: no bangumiSubjectId for animeId={}", animeId);
            return;
        }

        // 3. 映射状态
        Integer bangumiType = mapLocalStatusToBangumiType(localStatus);
        if (bangumiType == null) {
            log.debug("Bangumi sync skipped: unknown localStatus={}", localStatus);
            return;
        }

        // 4. 调用 Bangumi API
        String payload = "{\"type\":" + bangumiType + "}";
        ResponseEntity<String> response = bangumiApiService.postUserCollection(accessToken, subjectId, payload);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Bangumi sync success: userId={}, animeId={}, subjectId={}, status={} -> type={}",
                    userId, animeId, subjectId, localStatus, bangumiType);
            // 标记"看过"时，批量将所有本篇剧集也标记为看过
            if (bangumiType == BANGUMI_TYPE_DONE) {
                batchMarkAllEpisodesWatched(accessToken, subjectId);
            }
        } else if (response.getStatusCode().value() == 401) {
            log.warn("Bangumi sync failed: token expired for userId={}", userId);
        } else {
            log.warn("Bangumi sync failed: HTTP {} for userId={}, subjectId={}, payload={}",
                    response.getStatusCode().value(), userId, subjectId, payload);
        }
    }

    /**
     * 将本地"放弃追番"同步为 Bangumi 收藏的"抛弃"状态（type=5）。
     */
    private void doSyncUnfollow(Long userId, Long animeId, Long bangumiSubjectId) {
        // 1. 获取用户 access token
        String accessToken = getBangumiAccessToken(userId);
        if (accessToken == null) {
            log.debug("Bangumi unfollow sync skipped: no access token for userId={}", userId);
            return;
        }

        // 2. 确定 Bangumi subject ID：优先用追番记录上的，缺失时通过 animeId 反查
        if (bangumiSubjectId == null && animeId != null) {
            bangumiSubjectId = getBangumiSubjectId(animeId);
        }
        if (bangumiSubjectId == null) {
            log.debug("Bangumi unfollow sync skipped: no bangumiSubjectId for userId={}, animeId={}",
                    userId, animeId);
            return;
        }

        // 3. 调用"修改收藏"接口，把收藏标记为"抛弃"。
        //    使用 PATCH 而非 POST：未收藏时返回 404 直接跳过，避免误新建一条"抛弃"收藏。
        String payload = "{\"type\":" + BANGUMI_TYPE_DROPPED + "}";
        ResponseEntity<String> response = bangumiApiService.patchUserCollection(accessToken, bangumiSubjectId, payload);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Bangumi unfollow sync success: userId={}, animeId={}, subjectId={} -> type={} (dropped)",
                    userId, animeId, bangumiSubjectId, BANGUMI_TYPE_DROPPED);
        } else if (response.getStatusCode().value() == 401) {
            log.warn("Bangumi unfollow sync failed: token expired for userId={}", userId);
        } else if (response.getStatusCode().value() == 404) {
            log.debug("Bangumi unfollow sync skipped: subject not collected for userId={}, subjectId={}",
                    userId, bangumiSubjectId);
        } else {
            log.warn("Bangumi unfollow sync failed: HTTP {} for userId={}, subjectId={}, payload={}",
                    response.getStatusCode().value(), userId, bangumiSubjectId, payload);
        }
    }

    /**
     * 同步单集"已看"状态到 Bangumi。
     */
    private void doSyncEpisodeWatched(Long userId, Long animeId, String episodeNumber) {
        // 1. 获取用户 access token
        String accessToken = getBangumiAccessToken(userId);
        if (accessToken == null) {
            log.debug("Bangumi episode sync skipped: no access token for userId={}", userId);
            return;
        }

        // 2. 获取 Bangumi subject ID
        Long subjectId = getBangumiSubjectId(animeId);
        if (subjectId == null) {
            log.debug("Bangumi episode sync skipped: no bangumiSubjectId for animeId={}", animeId);
            return;
        }

        // 3. 获取 Bangumi 剧集 ID
        Long bangumiEpisodeId = getBangumiEpisodeId(subjectId, episodeNumber);
        if (bangumiEpisodeId == null) {
            log.warn("Bangumi episode sync skipped: cannot map episodeNumber={} for subjectId={}",
                    episodeNumber, subjectId);
            return;
        }

        // 4. 标记剧集为"看过"
        String payload = "{\"type\":" + EPISODE_TYPE_WATCHED + "}";
        ResponseEntity<String> response = bangumiApiService.putUserEpisodeCollection(
                accessToken, bangumiEpisodeId, payload);

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Bangumi episode sync success: userId={}, subjectId={}, episodeNumber={}, bgmEpisodeId={}",
                    userId, subjectId, episodeNumber, bangumiEpisodeId);
            return;
        }

        // 5. 如果返回 400，可能是条目尚未收藏，先创建"在看"收藏再重试
        if (response.getStatusCode().value() == 400) {
            log.debug("Bangumi episode sync: subject not collected yet, creating 在看 collection first. subjectId={}", subjectId);
            String collectPayload = "{\"type\":" + BANGUMI_TYPE_DOING + "}";
            ResponseEntity<String> collectResp = bangumiApiService.postUserCollection(
                    accessToken, subjectId, collectPayload);
            if (collectResp.getStatusCode().is2xxSuccessful() || collectResp.getStatusCode().value() == 204) {
                // 收藏创建成功，重试标记剧集
                ResponseEntity<String> retryResp = bangumiApiService.putUserEpisodeCollection(
                        accessToken, bangumiEpisodeId, payload);
                if (retryResp.getStatusCode().is2xxSuccessful()) {
                    log.info("Bangumi episode sync success (after auto-collect): userId={}, subjectId={}, episodeNumber={}",
                            userId, subjectId, episodeNumber);
                } else {
                    log.warn("Bangumi episode sync failed after auto-collect: HTTP {} for episodeId={}",
                            retryResp.getStatusCode().value(), bangumiEpisodeId);
                }
            } else {
                log.warn("Bangumi episode sync failed: cannot auto-collect subjectId={}, HTTP {}",
                        subjectId, collectResp.getStatusCode().value());
            }
            return;
        }

        if (response.getStatusCode().value() == 401) {
            log.warn("Bangumi episode sync failed: token expired for userId={}", userId);
        } else {
            log.warn("Bangumi episode sync failed: HTTP {} for episodeId={}",
                    response.getStatusCode().value(), bangumiEpisodeId);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取用户的 Bangumi access token。
     */
    private String getBangumiAccessToken(Long userId) {
        Optional<User> userOpt = userService.findById(userId);
        if (userOpt.isEmpty()) return null;
        User user = userOpt.get();
        if (!StringUtils.hasText(user.getBangumiAccessToken())) return null;
        return user.getBangumiAccessToken().trim();
    }

    /**
     * 获取番剧对应的 Bangumi subject ID。
     */
    private Long getBangumiSubjectId(Long animeId) {
        Optional<Anime> animeOpt = animeRepository.findByAnimeId(animeId);
        return animeOpt.map(Anime::getBangumiSubjectId).orElse(null);
    }

    /**
     * 将本地追番状态映射为 Bangumi 收藏类型。
     */
    private Integer mapLocalStatusToBangumiType(String localStatus) {
        if (localStatus == null) return null;
        return switch (localStatus.toLowerCase()) {
            case "wish"     -> BANGUMI_TYPE_WISH;
            case "watched"  -> BANGUMI_TYPE_DONE;
            case "completed"-> BANGUMI_TYPE_DONE;   // 兼容旧值
            case "watching" -> BANGUMI_TYPE_DOING;
            case "on_hold"  -> BANGUMI_TYPE_ON_HOLD;
            case "dropped"  -> BANGUMI_TYPE_DROPPED;
            default -> null;
        };
    }

    /**
     * 批量将条目的所有本篇剧集标记为"看过"。
     */
    private void batchMarkAllEpisodesWatched(String accessToken, Long subjectId) {
        try {
            ResponseEntity<String> response = bangumiApiService.getEpisodes(subjectId, 0, 200, 0);
            if (!response.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(response.getBody())) {
                log.warn("Bangumi batch mark: getEpisodes failed for subjectId={}, HTTP {}",
                        subjectId, response.getStatusCode().value());
                return;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.size() == 0) {
                return;
            }

            // 收集所有本篇剧集的 Bangumi episode ID
            var episodeIds = new java.util.ArrayList<Long>();
            for (JsonNode ep : data) {
                long epId = ep.get("id").asLong(-1);
                if (epId > 0) {
                    episodeIds.add(epId);
                }
            }
            if (episodeIds.isEmpty()) return;

            // 批量标记为看过
            String payload = String.format("{\"episode_id\":%s,\"type\":2}",
                    episodeIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",", "[", "]")));
            ResponseEntity<String> batchResp = bangumiApiService.patchUserSubjectEpisodeCollection(
                    accessToken, subjectId, payload);
            if (batchResp.getStatusCode().is2xxSuccessful()) {
                log.info("Bangumi batch mark success: subjectId={}, {} episodes marked watched",
                        subjectId, episodeIds.size());
            } else {
                log.warn("Bangumi batch mark failed: subjectId={}, episodes={}, HTTP {}",
                        subjectId, episodeIds.size(), batchResp.getStatusCode().value());
            }
        } catch (Exception e) {
            log.warn("Bangumi batch mark error for subjectId={}: {}", subjectId, e.getMessage());
        }
    }

    /**
     * 获取 Bangumi 剧集 ID（带缓存）。
     * 通过 Bangumi API 获取条目所有本篇剧集，按集数匹配。
     */
    private Long getBangumiEpisodeId(Long subjectId, String episodeNumber) {
        String cacheKey = subjectId + ":" + episodeNumber;

        // 检查缓存
        CachedEpisodeId cached = episodeIdCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            return cached.episodeId;
        }

        // 缓存未命中或已过期，从 API 获取
        try {
            ResponseEntity<String> response = bangumiApiService.getEpisodes(subjectId, 0, 200, 0);
            if (!response.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(response.getBody())) {
                log.warn("Bangumi getEpisodes failed: HTTP {} for subjectId={}",
                        response.getStatusCode().value(), subjectId);
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                log.warn("Bangumi getEpisodes returned unexpected format for subjectId={}", subjectId);
                return null;
            }

            // 遍历剧集，建立 ep → id 映射并填充缓存
            Long matchedId = null;
            for (JsonNode ep : data) {
                int epNum = ep.get("ep").asInt(-1);
                long epId = ep.get("id").asLong(-1);
                if (epNum > 0 && epId > 0) {
                    String key = subjectId + ":" + epNum;
                    episodeIdCache.put(key, new CachedEpisodeId(epId));
                    if (String.valueOf(epNum).equals(episodeNumber)) {
                        matchedId = epId;
                    }
                }
            }

            if (matchedId == null) {
                log.debug("Bangumi episode mapping not found: subjectId={}, episodeNumber={} ({} episodes loaded)",
                        subjectId, episodeNumber, data.size());
            }
            return matchedId;
        } catch (Exception e) {
            log.warn("Bangumi episode mapping failed for subjectId={}, episodeNumber={}: {}",
                    subjectId, episodeNumber, e.getMessage());
            return null;
        }
    }
}
