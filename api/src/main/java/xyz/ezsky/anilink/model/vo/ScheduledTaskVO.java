package xyz.ezsky.anilink.model.vo;

import lombok.Data;

/**
 * 定时任务视图对象，用于管理后台展示与手动触发。
 */
@Data
public class ScheduledTaskVO {

    /** 任务唯一 ID */
    private String id;

    /** 任务名称 */
    private String name;

    /** 任务描述 */
    private String description;

    /** 触发类型：cron / fixedDelay */
    private String type;

    /** cron 表达式（type=cron 时） */
    private String cron;

    /** 固定延迟毫秒数（type=fixedDelay 时） */
    private Long fixedDelayMillis;

    /** 是否启用（关闭后不再自动执行，手动触发仍可用） */
    private boolean enabled;

    /** 是否允许用户在管理后台手动开关 */
    private boolean toggleable;

    /** 最近触发时间 */
    private String lastTriggeredAt;

    /** 最近完成时间 */
    private String lastCompletedAt;

    /** 最近一次耗时（毫秒） */
    private Long lastDurationMs;

    /** 最近状态：never / running / success / error */
    private String lastStatus;

    /** 最近一次执行结果说明（失败原因） */
    private String lastResult;

    /** 预计下次触发时间 */
    private String nextRunAt;

    /** 是否正在执行中 */
    private boolean running;
}
