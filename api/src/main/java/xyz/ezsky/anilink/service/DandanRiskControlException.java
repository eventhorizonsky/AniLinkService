package xyz.ezsky.anilink.service;

/**
 * 弹弹接口风控/限流异常。
 *
 * <p>当弹弹接口返回限流（429）、风控（403）或服务不可用（5xx）时抛出，
 * 由 Spring Retry 自动重试。</p>
 */
public class DandanRiskControlException extends RuntimeException {

    public DandanRiskControlException(String message) {
        super(message);
    }

    public DandanRiskControlException(String message, Throwable cause) {
        super(message, cause);
    }
}
