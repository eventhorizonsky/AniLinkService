package xyz.ezsky.anilink.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import xyz.ezsky.anilink.util.DandanClientUtil;

/**
 * 弹弹接口限流调用器。
 *
 * <p>弹弹接口对调用频率有风控限制（过高频率会触发风控），因此此处做了两层保护：</p>
 * <ol>
 *   <li><strong>频率限制</strong>：全局限流，保证对弹弹接口的调用最多每秒 1 次；</li>
 *   <li><strong>自动重试</strong>：触发风控/限流（429/403）或服务不可用（5xx）时，
 *       通过 spring-retry 按指数退避自动重试。</li>
 * </ol>
 */
@Log4j2
@Component
public class DandanRateLimitedFetcher {

    /** 弹弹接口最小调用间隔（毫秒），最多每秒一次 */
    private static final long MIN_CALL_INTERVAL_MS = 1000L;

    private final DandanClientUtil dandanClientUtil;
    private final Object rateLimitLock = new Object();
    private long lastCallAt = 0L;

    public DandanRateLimitedFetcher(DandanClientUtil dandanClientUtil) {
        this.dandanClientUtil = dandanClientUtil;
    }

    /**
     * 以最多每秒一次的频率请求弹弹接口，触发风控时自动重试。
     *
     * @param baseUrl 弹弹接口基础地址
     * @param path    请求路径，如 /api/v2/bangumi/{animeId}
     * @return 响应
     */
    @Retryable(
            retryFor = DandanRiskControlException.class,
            maxAttempts = 5,
            backoff = @Backoff(delay = 3000, multiplier = 2)
    )
    public ResponseEntity<String> get(String baseUrl, String path) {
        rateLimit();
        ResponseEntity<String> response;
        try {
            response = dandanClientUtil.get(baseUrl, path);
        } catch (Exception e) {
            log.warn("弹弹接口请求异常，等待重试 path={}: {}", path, e.getMessage());
            throw new DandanRiskControlException("弹弹接口请求异常: " + e.getMessage(), e);
        }

        int code = response.getStatusCode().value();
        if (code == 429 || code == 403 || code >= 500) {
            log.warn("弹弹接口触发风控/限流 (HTTP {}), path={}，等待重试", code, path);
            throw new DandanRiskControlException("弹弹接口触发风控/限流 (HTTP " + code + ")");
        }
        return response;
    }

    /**
     * 限流：保证相邻两次调用间隔不小于 {@link #MIN_CALL_INTERVAL_MS}。
     */
    private void rateLimit() {
        long waitMs;
        long callAt;
        synchronized (rateLimitLock) {
            long now = System.currentTimeMillis();
            long gap = now - lastCallAt;
            if (gap < MIN_CALL_INTERVAL_MS) {
                waitMs = MIN_CALL_INTERVAL_MS - gap;
                callAt = now + waitMs;
            } else {
                waitMs = 0;
                callAt = now;
            }
            lastCallAt = callAt;
        }
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
