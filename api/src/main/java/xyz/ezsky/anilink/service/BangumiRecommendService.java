package xyz.ezsky.anilink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import xyz.ezsky.anilink.model.entity.BangumiRecommendRecord;
import xyz.ezsky.anilink.model.entity.User;
import xyz.ezsky.anilink.repository.BangumiRecommendRecordRepository;
import xyz.ezsky.anilink.repository.UserRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Bangumi「猜你喜欢」全站个性化推荐。
 *
 * <p>算法移植自 czy0729/Bangumi 客户端（src/screens/discovery/like）：拉取用户各状态收藏 →
 * 为每个收藏条目取官方推荐（数据源改为 next API /p1/subjects/{id}/recs，替代作者自建快照与
 * 分类排行兜底）→ 按 10 个可开关维度对候选条目加权累计评分 → 剔除已收藏、按推荐分降序输出。</p>
 *
 * <p>每次生成均从 Bangumi 实时重新拉取收藏与推荐（结果落库），由用户在页面主动触发；
 * 生成过程异步执行，前端通过 /status 轮询进度。</p>
 */
@Service
@Log4j2
public class BangumiRecommendService {

    /** 推荐理由（维度）名称，下标即维度开关下标 */
    public static final String[] REASONS = {
            "自己评分", "收藏状态", "条目排名", "条目分数", "已看集数",
            "自己点评", "私密收藏", "最近收藏", "标签倾向", "多次推荐"
    };

    /** 维度开关说明 */
    public static final String[] REASONS_INFO = {
            "已评分，高分多加分，低分多扣分",
            "想看加分，搁置、抛弃扣分",
            "收藏条目自身高排名加分，低排名扣分",
            "收藏条目自身高分加分，低分扣分",
            "已看过集数多，稍微加分",
            "进行过长评稍微加分",
            "私密收藏加分",
            "最近操作过稍微加分",
            "标签是你倾向打的，越多相对加越多分",
            "被多个收藏条目同时推荐到相对加分"
    };

    /**
     * 各收藏状态的拉取上限（页，50/页），与 czy0729/Bangumi 客户端一致：
     * 想看 200 / 在看 300 / 看过 600 / 搁置 100 / 抛弃 100。
     * v0 type：1=想看 2=在看 3=看过 4=搁置 5=抛弃
     */
    private static final int[][] STATUS_PAGES = {{1, 4}, {2, 6}, {3, 12}, {4, 2}, {5, 2}};

    private static final int PAGE_LIMIT = 50;
    /** p1 recs 单条目推荐数（接口上限 10） */
    private static final int RECS_LIMIT = 10;
    /** 结果最多保留条数 */
    private static final int MAX_RESULT = 200;
    /** 时间类标签（纯数字或含 年/月/改），不参与标签倾向统计 */
    private static final Pattern TIME_PATTERN = Pattern.compile("^\\d+$|^.*([年月改]).*$");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private BangumiApiService bangumiApiService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BangumiRecommendRecordRepository recordRepository;

    /** 生成任务线程（每用户同时最多一个任务） */
    private final ExecutorService generateExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "bgm-recommend-generate");
        t.setDaemon(true);
        return t;
    });

    /** recs 并发拉取线程（同时访问 next API 的并发度，避免压力过大） */
    private final ExecutorService recsExecutor = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "bgm-recommend-recs");
        t.setDaemon(true);
        return t;
    });

    /** 每用户任务状态（内存态，重启丢失不影响已落库结果） */
    private final Map<Long, TaskState> tasks = new ConcurrentHashMap<>();

    /** 业务异常（未绑定 / 授权失效等） */
    public static class BangumiRecommendException extends RuntimeException {
        public BangumiRecommendException(String message) {
            super(message);
        }
    }

    /** 异步任务进度 */
    public static class TaskState {
        public volatile String state = "RUNNING";
        public volatile String phase = "准备中";
        public volatile String message = "";
        public final AtomicInteger current = new AtomicInteger(0);
        public volatile int total = 0;
        public volatile long startedAt = System.currentTimeMillis();
    }

    // ================================ 对外接口 ================================

    /**
     * 启动（或返回进行中的）推荐生成任务。
     *
     * @param dimensions 10 位维度开关（null/长度不足按全开处理）
     */
    public Map<String, Object> startGenerate(Long userId, List<Boolean> dimensions) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BangumiRecommendException("用户不存在"));
        if (!StringUtils.hasText(user.getBangumiAccessToken())) {
            throw new BangumiRecommendException("未绑定 Bangumi 账号，请先在「账号绑定」页完成绑定");
        }
        if (!StringUtils.hasText(user.getBangumiUsername()) && user.getBangumiUserId() == null) {
            throw new BangumiRecommendException("Bangumi 账号信息不完整，请重新绑定");
        }
        boolean[] dims = normalizeDimensions(dimensions);

        TaskState existing = tasks.get(userId);
        if (existing != null && "RUNNING".equals(existing.state)) {
            return buildTaskPayload(existing);
        }

        TaskState state = new TaskState();
        tasks.put(userId, state);
        generateExecutor.submit(() -> {
            try {
                runGenerate(userId, user, dims, state);
            } catch (Exception e) {
                // runGenerate 内部已兜底，此处防御二次异常
                state.state = "ERROR";
                state.message = "生成失败：" + e.getMessage();
                log.error("猜你喜欢生成任务异常 userId={}", userId, e);
            }
        });
        return buildTaskPayload(state);
    }

    /** 任务进度 + 落库结果摘要 */
    public Map<String, Object> getStatus(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        boolean bound = user != null && StringUtils.hasText(user.getBangumiAccessToken());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("bound", bound);
        TaskState state = tasks.get(userId);
        payload.put("generating", state != null && "RUNNING".equals(state.state));
        payload.put("progress", state == null ? null : buildTaskPayload(state));

        Map<String, Object> record = new LinkedHashMap<>();
        record.put("hasResult", false);
        recordRepository.findByUserId(userId).ifPresent(r -> {
            record.put("hasResult", true);
            record.put("generatedAt", r.getGeneratedAt());
            record.put("collectionCount", r.getCollectionCount());
            record.put("resultCount", r.getResultCount());
            record.put("dimensions", parseDimensions(r.getDimensions()));
        });
        payload.put("record", record);
        return payload;
    }

    /** 读取落库的完整推荐结果 */
    public Map<String, Object> getResult(Long userId) {
        BangumiRecommendRecord r = recordRepository.findByUserId(userId)
                .orElseThrow(() -> new BangumiRecommendException("还没有生成过推荐，请先点击「生成推荐」"));
        List<Object> list;
        try {
            list = MAPPER.readValue(r.getResultJson(), MAPPER.getTypeFactory()
                    .constructCollectionType(List.class, Object.class));
        } catch (Exception e) {
            throw new BangumiRecommendException("推荐结果数据损坏，请重新生成");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", r.getGeneratedAt());
        payload.put("collectionCount", r.getCollectionCount());
        payload.put("resultCount", r.getResultCount());
        payload.put("dimensions", parseDimensions(r.getDimensions()));
        payload.put("list", list);
        return payload;
    }

    // ================================ 生成流程 ================================

    private void runGenerate(Long userId, User user, boolean[] dims, TaskState state) {
        try {
            String token = user.getBangumiAccessToken();
            String username = StringUtils.hasText(user.getBangumiUsername())
                    ? user.getBangumiUsername() : String.valueOf(user.getBangumiUserId());

            // 1. 重新拉取全部动画收藏
            state.phase = "拉取 Bangumi 收藏";
            List<CollectionItem> collections = fetchCollections(token, username, state);
            if (collections.isEmpty()) {
                throw new BangumiRecommendException("没有获取到收藏数据，可能授权已过期，请重新绑定后再试");
            }

            // 2. 标签倾向值：统计用户自己打的标签频率（忽略时间类标签），条目 rec = 其用户标签频率之和
            Map<String, Integer> tagFreq = new HashMap<>();
            for (CollectionItem item : collections) {
                for (String tag : item.userTags) {
                    tagFreq.merge(tag, 1, Integer::sum);
                }
            }
            for (CollectionItem item : collections) {
                int rec = 0;
                for (String tag : item.userTags) {
                    rec += tagFreq.getOrDefault(tag, 0);
                }
                item.rec = rec;
            }

            // 3. 逐收藏条目拉取官方推荐
            state.phase = "获取条目推荐";
            state.current.set(0);
            state.total = collections.size();
            Map<Long, List<RecItem>> recsMap = fetchRecs(collections, state);

            // 4. 聚合评分
            state.phase = "计算推荐结果";
            List<Map<String, Object>> result = compute(collections, recsMap, dims);

            // 5. 落库
            String resultJson = MAPPER.writeValueAsString(result);
            BangumiRecommendRecord record = recordRepository.findByUserId(userId)
                    .orElseGet(BangumiRecommendRecord::new);
            record.setUserId(userId);
            record.setDimensions(dimensionString(dims));
            record.setResultJson(resultJson);
            record.setCollectionCount(collections.size());
            record.setResultCount(result.size());
            record.setGeneratedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());
            recordRepository.save(record);

            state.state = "DONE";
            state.phase = "完成";
            state.message = "";
            log.info("猜你喜欢生成完成 userId={} collections={} result={}", userId, collections.size(), result.size());
        } catch (Exception e) {
            state.state = "ERROR";
            state.message = e instanceof BangumiRecommendException
                    ? e.getMessage() : "生成失败：" + e.getMessage();
            log.error("猜你喜欢生成失败 userId={}", userId, e);
        }
    }

    /** 分状态分页拉取收藏，页数按 STATUS_PAGES 上限，依据 total 提前终止 */
    private List<CollectionItem> fetchCollections(String token, String username, TaskState state) {
        int totalRequests = 0;
        for (int[] sp : STATUS_PAGES) {
            totalRequests += sp[1];
        }
        state.current.set(0);
        state.total = totalRequests;

        List<CollectionItem> all = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int[] sp : STATUS_PAGES) {
            int collectionType = sp[0];
            int maxPages = sp[1];
            for (int page = 1; page <= maxPages; page++) {
                int offset = (page - 1) * PAGE_LIMIT;
                String raw = bangumiApiService.getUserCollections(
                        token, username, 2, collectionType, PAGE_LIMIT, offset).getBody();
                state.current.incrementAndGet();
                int loaded = parseCollections(raw, all, seen);
                // total 提前终止（返回 total 为该状态总条数）
                if (loaded < 0 || (page * PAGE_LIMIT) >= totalOf(raw)) {
                    break;
                }
                try {
                    Thread.sleep(60);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new BangumiRecommendException("任务被中断");
                }
            }
        }
        return all;
    }

    /** 解析一页收藏，去重后追加；返回本页条数（解析失败返回 -1） */
    private int parseCollections(String raw, List<CollectionItem> out, Set<Long> seen) {
        if (!StringUtils.hasText(raw)) {
            return -1;
        }
        try {
            JsonNode data = MAPPER.readTree(raw).path("data");
            if (!data.isArray()) {
                return -1;
            }
            int count = 0;
            for (JsonNode node : data) {
                long id = node.path("subject_id").asLong(node.path("subject").path("id").asLong(0));
                if (id <= 0 || !seen.add(id)) {
                    continue;
                }
                CollectionItem item = new CollectionItem();
                item.id = id;
                item.rate = node.path("rate").asInt(0);
                item.v0Type = node.path("type").asInt(0);
                item.ep = node.path("ep_status").asInt(0);
                String comment = node.path("comment").asText("");
                item.commentLen = comment == null ? 0 : comment.length();
                item.priv = node.path("private").asBoolean(false);
                item.diffDays = parseDayDiff(node.path("updated_at").asText(null));
                item.userTags = new ArrayList<>();
                JsonNode tags = node.path("tags");
                if (tags.isArray()) {
                    for (JsonNode t : tags) {
                        String name = t.asText("");
                        if (StringUtils.hasText(name) && !TIME_PATTERN.matcher(name).matches()) {
                            item.userTags.add(name);
                        }
                    }
                }
                JsonNode subject = node.path("subject");
                item.rank = subject.path("rank").asInt(0);
                item.score = subject.path("score").asDouble(0);
                out.add(item);
                count++;
            }
            return count;
        } catch (Exception e) {
            log.warn("解析收藏响应失败", e);
            return -1;
        }
    }

    private int totalOf(String raw) {
        try {
            return MAPPER.readTree(raw == null ? "" : raw).path("total").asInt(Integer.MAX_VALUE);
        } catch (Exception e) {
            return Integer.MAX_VALUE;
        }
    }

    /** 并发拉取每个收藏条目的官方推荐（p1 recs），失败按空处理 */
    private Map<Long, List<RecItem>> fetchRecs(List<CollectionItem> collections, TaskState state) {
        Map<Long, List<RecItem>> recsMap = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>(collections.size());
        for (CollectionItem item : collections) {
            futures.add(CompletableFuture.runAsync(() -> {
                List<RecItem> recs = new ArrayList<>();
                try {
                    String raw = bangumiApiService.getP1SubjectRecsRaw(item.id, RECS_LIMIT);
                    if (StringUtils.hasText(raw)) {
                        JsonNode data = MAPPER.readTree(raw).path("data");
                        if (data.isArray()) {
                            for (JsonNode node : data) {
                                JsonNode subject = node.path("subject");
                                long id = subject.path("id").asLong(0);
                                // 本站以动画为主，仅保留动画类型的推荐
                                if (id <= 0 || subject.path("type").asInt(0) != 2) {
                                    continue;
                                }
                                RecItem rec = new RecItem();
                                rec.id = id;
                                rec.name = subject.path("name").asText("");
                                rec.nameCn = subject.path("nameCN").asText("");
                                JsonNode images = subject.path("images");
                                rec.image = firstText(images, "common", "large", "medium");
                                rec.score = subject.path("rating").path("score").asDouble(0);
                                rec.rank = subject.path("rating").path("rank").asInt(0);
                                JsonNode metaTags = subject.path("metaTags");
                                if (metaTags.isArray()) {
                                    for (JsonNode t : metaTags) {
                                        rec.metaTags.add(t.asText(""));
                                    }
                                }
                                rec.sim = node.path("sim").asDouble(0);
                                recs.add(rec);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("拉取条目推荐失败 subjectId={}", item.id);
                } finally {
                    recsMap.put(item.id, recs);
                    state.current.incrementAndGet();
                }
            }, recsExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return recsMap;
    }

    /** 聚合评分：候选 = 各收藏条目推荐中出现且未收藏的条目；rate/reasons 跨来源累计 */
    private List<Map<String, Object>> compute(List<CollectionItem> collections,
                                              Map<Long, List<RecItem>> recsMap, boolean[] dims) {
        Set<Long> collectedIds = new HashSet<>();
        for (CollectionItem item : collections) {
            collectedIds.add(item.id);
        }

        Map<Long, Candidate> candidates = new HashMap<>();
        for (CollectionItem item : collections) {
            List<RecItem> recs = recsMap.getOrDefault(item.id, Collections.emptyList());
            for (RecItem rec : recs) {
                if (collectedIds.contains(rec.id)) {
                    continue;
                }
                Candidate c = candidates.computeIfAbsent(rec.id, k -> new Candidate(rec));
                int[] reasons = calc(item, c.relatesCount(), dims);
                double rate = sum(reasons) + rec.sim * 2;
                c.rate += rate;
                for (int i = 0; i < reasons.length; i++) {
                    c.reasons[i] += reasons[i];
                }
                c.relates.add(item.id);
            }
        }

        List<Candidate> sorted = new ArrayList<>(candidates.values());
        sorted.sort((a, b) -> Double.compare(b.rate, a.rate));

        List<Map<String, Object>> result = new ArrayList<>(Math.min(sorted.size(), MAX_RESULT));
        for (Candidate c : sorted) {
            if (result.size() >= MAX_RESULT) {
                break;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id);
            m.put("name", c.name);
            m.put("nameCn", c.nameCn);
            m.put("cover", c.image);
            m.put("score", c.score);
            m.put("rank", c.rank);
            m.put("rate", Math.round(c.rate * 10) / 10.0);
            m.put("relates", c.relates.size());
            List<String> tags = new ArrayList<>(3);
            for (String t : c.metaTags) {
                if (tags.size() >= 3) {
                    break;
                }
                if (StringUtils.hasText(t)) {
                    tags.add(t);
                }
            }
            m.put("tags", tags);
            List<Map<String, Object>> reasonList = new ArrayList<>();
            for (int i = 0; i < REASONS.length; i++) {
                if (c.reasons[i] != 0) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    // 只落维度 key 与贡献值：label 可由 key 在 REASONS 中推导，
                    // 避免 200 条结果里每行重复 10 个中文维度名把 result_json 撑大
                    r.put("key", i);
                    r.put("value", c.reasons[i]);
                    reasonList.add(r);
                }
            }
            reasonList.sort((a, b) -> Math.abs((int) b.get("value")) - Math.abs((int) a.get("value")));
            m.put("reasons", reasonList);
            result.add(m);
        }
        return result;
    }

    /**
     * 单次推荐来源的维度得分（移植 czy0729 calc()，extraScore 改用官方 sim 相似度）。
     * 维度 0/1 对所有收藏状态生效，维度 2-9 仅对 想看/在看/看过 生效。
     */
    private int[] calc(CollectionItem item, int relatesCount, boolean[] dims) {
        int[] r = new int[REASONS.length];
        boolean canRec = item.v0Type == 1 || item.v0Type == 2 || item.v0Type == 3;

        // [0] 用户自己的打分：高分多加，低分多扣（未评分按 0 分处理，同样压低权重，与原实现一致）
        if (dims[0]) {
            if (item.rate >= 8) {
                r[0] = item.rate * 3;
            } else if (item.rate <= 4) {
                r[0] = Math.abs(item.rate - 5) * -3;
            } else {
                r[0] = item.rate;
            }
        }
        // [1] 用户观看状态：想看加分，搁置/抛弃扣分
        if (dims[1]) {
            if (item.v0Type == 1) {
                r[1] = 10;
            } else if (item.v0Type == 4) {
                r[1] = -40;
            } else if (item.v0Type == 5) {
                r[1] = -80;
            }
        }
        // [2] 收藏条目自身排名
        if (dims[2] && canRec && item.rank > 0) {
            if (item.rank <= 100) {
                r[2] = 20;
            } else if (item.rank <= 1000) {
                r[2] = 10;
            } else if (item.rank <= 2000) {
                r[2] = 5;
            } else if (item.rank >= 5000) {
                r[2] = -10;
            } else if (item.rank >= 4000) {
                r[2] = -5;
            }
        }
        // [3] 收藏条目自身评分（有排名的权重更高）
        if (dims[3] && canRec) {
            r[3] = item.rank > 0 ? (int) item.score : (int) (item.score / 2);
        }
        // [4] 收看集数
        if (dims[4] && canRec && item.ep >= 12) {
            r[4] = 10;
        }
        // [5] 长评
        if (dims[5] && canRec && item.commentLen >= 32) {
            r[5] = 10;
        }
        // [6] 私密收藏
        if (dims[6] && canRec && item.priv) {
            r[6] = 15;
        }
        // [7] 最近收藏时间
        if (dims[7] && canRec && item.diffDays != null) {
            if (item.diffDays <= 30) {
                r[7] = 10;
            } else if (item.diffDays >= 365) {
                r[7] = -10;
            }
        }
        // [8] 标签倾向值
        if (dims[8] && canRec && item.rec > 0) {
            r[8] = item.rec / 5;
        }
        // [9] 被多个收藏条目同时推荐到（首次出现不加分）
        if (dims[9] && canRec && relatesCount > 0) {
            r[9] = 4;
        }
        return r;
    }

    // ================================ 工具 ================================

    private static int sum(int[] arr) {
        int s = 0;
        for (int v : arr) {
            s += v;
        }
        return s;
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return "";
        }
        for (String f : fields) {
            JsonNode v = node.get(f);
            if (v != null && v.isTextual() && StringUtils.hasText(v.asText())) {
                return v.asText();
            }
        }
        return "";
    }

    /** 解析 ISO 时间与当前相差天数，失败返回 null（该维度不参与计分） */
    private static Integer parseDayDiff(String updatedAt) {
        if (!StringUtils.hasText(updatedAt)) {
            return null;
        }
        try {
            return (int) Math.abs(Duration.between(OffsetDateTime.parse(updatedAt),
                    OffsetDateTime.now()).toDays());
        } catch (Exception e) {
            return null;
        }
    }

    private boolean[] normalizeDimensions(List<Boolean> dimensions) {
        boolean[] dims = new boolean[REASONS.length];
        for (int i = 0; i < dims.length; i++) {
            dims[i] = dimensions == null || i >= dimensions.size()
                    || dimensions.get(i) == null || dimensions.get(i);
        }
        return dims;
    }

    private String dimensionString(boolean[] dims) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < dims.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(dims[i] ? 1 : 0);
        }
        return sb.append(']').toString();
    }

    private List<Boolean> parseDimensions(String json) {
        List<Boolean> list = new ArrayList<>(REASONS.length);
        for (int i = 0; i < REASONS.length; i++) {
            list.add(true);
        }
        try {
            JsonNode arr = MAPPER.readTree(json);
            if (arr.isArray()) {
                for (int i = 0; i < arr.size() && i < list.size(); i++) {
                    list.set(i, arr.get(i).asInt(1) == 1);
                }
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private Map<String, Object> buildTaskPayload(TaskState state) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("state", state.state);
        p.put("phase", state.phase);
        p.put("current", state.current.get());
        p.put("total", state.total);
        p.put("message", state.message);
        return p;
    }

    @PreDestroy
    public void shutdown() {
        generateExecutor.shutdownNow();
        recsExecutor.shutdownNow();
    }

    // ================================ 数据结构 ================================

    /** 用户收藏条目（已合并条目摘要） */
    private static class CollectionItem {
        long id;
        int rate;
        int v0Type;
        int ep;
        int commentLen;
        boolean priv;
        Integer diffDays;
        List<String> userTags;
        int rank;
        double score;
        int rec;
    }

    /** p1 recs 单条推荐 */
    private static class RecItem {
        long id;
        String name = "";
        String nameCn = "";
        String image = "";
        double score;
        int rank;
        List<String> metaTags = new ArrayList<>();
        double sim;
    }

    /** 聚合中的候选条目 */
    private static class Candidate {
        final long id;
        final String name;
        final String nameCn;
        final String image;
        final double score;
        final int rank;
        final List<String> metaTags;
        double rate;
        final int[] reasons = new int[REASONS.length];
        final Set<Long> relates = new HashSet<>();

        Candidate(RecItem rec) {
            this.id = rec.id;
            this.name = rec.name;
            this.nameCn = rec.nameCn;
            this.image = rec.image;
            this.score = rec.score;
            this.rank = rec.rank;
            this.metaTags = rec.metaTags;
        }

        int relatesCount() {
            return relates.size();
        }
    }
}
