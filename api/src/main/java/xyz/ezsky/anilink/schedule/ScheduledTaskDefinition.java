package xyz.ezsky.anilink.schedule;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务定义。
 *
 * <p>既包含任务元信息（名称、描述、触发方式），也包含运行期状态跟踪
 * （最近触发时间、最近完成时间、最近耗时、最近状态、最近结果）。</p>
 */
public class ScheduledTaskDefinition {

    public static final String TYPE_CRON = "cron";
    public static final String TYPE_FIXED_DELAY = "fixedDelay";

    private final String id;
    private final String name;
    private final String description;
    private final String type;
    private final String cron;
    private final Long fixedDelayMillis;
    private final Runnable task;

    /** 是否允许用户在管理后台手动开关（例如 RSS 轮询不可关闭） */
    private final boolean toggleable;

    /** 当前是否启用（关闭后不再自动执行，但手动触发仍可用） */
    private volatile boolean enabled = true;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile String lastStatus = "never";
    private volatile String lastResult;
    private volatile LocalDateTime lastTriggeredAt;
    private volatile LocalDateTime lastCompletedAt;
    private volatile long lastDurationMs;

    public ScheduledTaskDefinition(String id, String name, String description, String type,
                                   String cron, Long fixedDelayMillis, Runnable task) {
        this(id, name, description, type, cron, fixedDelayMillis, task, true);
    }

    public ScheduledTaskDefinition(String id, String name, String description, String type,
                                   String cron, Long fixedDelayMillis, Runnable task, boolean toggleable) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.cron = cron;
        this.fixedDelayMillis = fixedDelayMillis;
        this.task = task;
        this.toggleable = toggleable;
    }

    /**
     * 执行任务并跟踪运行状态。已在执行中时直接返回，避免并发重复执行。
     */
    public void run() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        lastTriggeredAt = LocalDateTime.now();
        lastStatus = "running";
        lastResult = null;
        long start = System.currentTimeMillis();
        try {
            task.run();
            lastStatus = "success";
        } catch (Exception e) {
            lastStatus = "error";
            lastResult = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        } finally {
            lastDurationMs = System.currentTimeMillis() - start;
            lastCompletedAt = LocalDateTime.now();
            running.set(false);
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getType() {
        return type;
    }

    public String getCron() {
        return cron;
    }

    public Long getFixedDelayMillis() {
        return fixedDelayMillis;
    }

    public boolean isToggleable() {
        return toggleable;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRunning() {
        return running.get();
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public String getLastResult() {
        return lastResult;
    }

    public LocalDateTime getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public LocalDateTime getLastCompletedAt() {
        return lastCompletedAt;
    }

    public long getLastDurationMs() {
        return lastDurationMs;
    }
}
