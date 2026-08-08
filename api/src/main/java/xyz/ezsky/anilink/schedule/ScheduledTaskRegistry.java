package xyz.ezsky.anilink.schedule;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import xyz.ezsky.anilink.service.SiteConfigService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务注册表。
 *
 * <p>所有定时任务统一在此注册，由 {@link TaskScheduler} 负责按 cron / fixedDelay 调度；
 * 同时提供立即触发与启停开关能力，供管理后台使用。调度执行与手动触发都会更新
 * {@link ScheduledTaskDefinition} 中的运行状态。</p>
 *
 * <p>开关状态通过 {@link SiteConfigService} 持久化，重启后保持用户的选择。</p>
 */
@Log4j2
@Component
public class ScheduledTaskRegistry {

    private final TaskScheduler taskScheduler;
    private final Executor asyncExecutor;
    private final SiteConfigService siteConfigService;
    private final Map<String, ScheduledTaskEntry> tasks = new LinkedHashMap<>();

    public ScheduledTaskRegistry(
            @Qualifier("scheduledTaskScheduler") TaskScheduler taskScheduler,
            @Qualifier("taskExecutor") Executor asyncExecutor,
            SiteConfigService siteConfigService) {
        this.taskScheduler = taskScheduler;
        this.asyncExecutor = asyncExecutor;
        this.siteConfigService = siteConfigService;
    }

    /**
     * 注册一个定时任务并开始按配置调度。
     * 若该任务曾被用户关闭，则恢复为关闭状态，不参与自动调度。
     */
    public void register(ScheduledTaskDefinition definition) {
        boolean enabled = siteConfigService.getScheduledTaskEnabled(definition.getId(), definition.isEnabled());
        definition.setEnabled(enabled);

        ScheduledTaskEntry entry = new ScheduledTaskEntry(definition);
        tasks.put(definition.getId(), entry);

        if (enabled) {
            entry.future = schedule(definition);
        } else {
            log.info("已注册定时任务 [{}] - {}（当前为关闭状态，不自动执行）", definition.getId(), definition.getName());
        }
    }

    public List<ScheduledTaskDefinition> getAll() {
        List<ScheduledTaskDefinition> result = new ArrayList<>();
        for (ScheduledTaskEntry entry : tasks.values()) {
            result.add(entry.definition);
        }
        return result;
    }

    public ScheduledTaskDefinition get(String id) {
        ScheduledTaskEntry entry = tasks.get(id);
        return entry == null ? null : entry.definition;
    }

    /**
     * 异步立即触发指定任务，不阻塞调用方。任务关闭状态不影响手动触发。
     *
     * @param id 任务 ID
     */
    public void triggerNow(String id) {
        ScheduledTaskDefinition definition = get(id);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "定时任务不存在: " + id);
        }
        asyncExecutor.execute(definition::run);
    }

    /**
     * 设置任务启停状态：开启后恢复自动调度，关闭后取消自动调度。
     *
     * @param id      任务 ID
     * @param enabled 是否启用
     */
    public void setEnabled(String id, boolean enabled) {
        ScheduledTaskEntry entry = tasks.get(id);
        if (entry == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "定时任务不存在: " + id);
        }
        if (!entry.definition.isToggleable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该定时任务不允许手动开关");
        }
        if (entry.definition.isEnabled() == enabled) {
            return;
        }

        ScheduledFuture<?> future;
        if (enabled) {
            entry.definition.setEnabled(true);
            future = schedule(entry.definition);
            log.info("已开启定时任务 [{}] - {}", id, entry.definition.getName());
        } else {
            entry.definition.setEnabled(false);
            if (entry.future != null) {
                entry.future.cancel(false);
            }
            future = null;
            log.info("已关闭定时任务 [{}] - {}", id, entry.definition.getName());
        }
        entry.future = future;

        try {
            siteConfigService.setScheduledTaskEnabled(id, enabled);
        } catch (Exception e) {
            log.warn("保存定时任务 [{}] 开关状态失败，重启后将恢复原状态", id, e);
        }
    }

    private ScheduledFuture<?> schedule(ScheduledTaskDefinition definition) {
        if (ScheduledTaskDefinition.TYPE_CRON.equals(definition.getType())) {
            return taskScheduler.schedule(definition::run, new CronTrigger(definition.getCron()));
        }
        if (ScheduledTaskDefinition.TYPE_FIXED_DELAY.equals(definition.getType())) {
            return taskScheduler.scheduleWithFixedDelay(definition::run, definition.getFixedDelayMillis());
        }
        throw new IllegalArgumentException("不支持的定时任务类型: " + definition.getType());
    }

    /**
     * 注册表条目：持有任务定义与其当前调度句柄。
     */
    private static class ScheduledTaskEntry {
        private final ScheduledTaskDefinition definition;
        private volatile ScheduledFuture<?> future;

        ScheduledTaskEntry(ScheduledTaskDefinition definition) {
            this.definition = definition;
        }
    }
}
