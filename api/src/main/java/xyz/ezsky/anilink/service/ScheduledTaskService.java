package xyz.ezsky.anilink.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import xyz.ezsky.anilink.model.vo.ScheduledTaskVO;
import xyz.ezsky.anilink.schedule.ScheduledTaskDefinition;
import xyz.ezsky.anilink.schedule.ScheduledTaskRegistry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 定时任务管理服务。
 *
 * <p>向管理后台提供定时任务列表与立即触发能力。</p>
 */
@Service
public class ScheduledTaskService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ScheduledTaskRegistry scheduledTaskRegistry;

    /**
     * 获取所有已注册的定时任务。
     */
    public List<ScheduledTaskVO> listTasks() {
        return scheduledTaskRegistry.getAll().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 立即异步触发指定定时任务。
     *
     * @param id 任务 ID
     * @return 触发后的任务视图对象
     */
    public ScheduledTaskVO triggerNow(String id) {
        ScheduledTaskDefinition definition = scheduledTaskRegistry.get(id);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "定时任务不存在: " + id);
        }
        scheduledTaskRegistry.triggerNow(id);
        return convertToVO(definition);
    }

    /**
     * 设置指定定时任务的启停状态。
     *
     * @param id      任务 ID
     * @param enabled 是否启用
     * @return 更新后的任务视图对象
     */
    public ScheduledTaskVO setEnabled(String id, boolean enabled) {
        scheduledTaskRegistry.setEnabled(id, enabled);
        ScheduledTaskDefinition definition = scheduledTaskRegistry.get(id);
        if (definition == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "定时任务不存在: " + id);
        }
        return convertToVO(definition);
    }

    private ScheduledTaskVO convertToVO(ScheduledTaskDefinition definition) {
        ScheduledTaskVO vo = new ScheduledTaskVO();
        vo.setId(definition.getId());
        vo.setName(definition.getName());
        vo.setDescription(definition.getDescription());
        vo.setType(definition.getType());
        vo.setCron(definition.getCron());
        vo.setFixedDelayMillis(definition.getFixedDelayMillis());
        vo.setEnabled(definition.isEnabled());
        vo.setToggleable(definition.isToggleable());
        vo.setLastTriggeredAt(format(definition.getLastTriggeredAt()));
        vo.setLastCompletedAt(format(definition.getLastCompletedAt()));
        vo.setLastDurationMs(definition.getLastStatus().equals("never") ? null : definition.getLastDurationMs());
        vo.setLastStatus(definition.getLastStatus());
        vo.setLastResult(definition.getLastResult());
        vo.setRunning(definition.isRunning());
        vo.setNextRunAt(computeNextRunAt(definition));
        return vo;
    }

    /**
     * 估算下次触发时间：cron 任务用 Spring CronExpression 计算，
     * fixedDelay 任务按最近完成时间 + 固定延迟估算（从未执行则为当前时间 + 延迟）。
     */
    private String computeNextRunAt(ScheduledTaskDefinition definition) {
        LocalDateTime now = LocalDateTime.now();
        if (ScheduledTaskDefinition.TYPE_CRON.equals(definition.getType())) {
            if (definition.getCron() == null) {
                return null;
            }
            LocalDateTime next;
            try {
                next = CronExpression.parse(definition.getCron()).next(now);
            } catch (Exception e) {
                return null;
            }
            return format(next);
        }
        if (ScheduledTaskDefinition.TYPE_FIXED_DELAY.equals(definition.getType()) && definition.getFixedDelayMillis() != null) {
            LocalDateTime base = definition.getLastCompletedAt() != null
                    ? definition.getLastCompletedAt() : now;
            return format(base.plusNanos(definition.getFixedDelayMillis() * 1_000_000L));
        }
        return null;
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.format(FORMATTER);
    }
}
