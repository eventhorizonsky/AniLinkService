package xyz.ezsky.anilink.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一API响应模型
 * @param <T> 响应数据类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseVO<T> {
    
    /**
     * 响应状态码
     */
    private int code;
    
    /**
     * 响应消息
     */
    private String msg;
    
    /**
     * 响应数据
     */
    private T data;
    
    /**
     * 成功响应
     */
    public static <T> ApiResponseVO<T> success(T data) {
        return new ApiResponseVO<>(200, "success", data);
    }
    
    /**
     * 成功响应（带自定义消息）
     */
    public static <T> ApiResponseVO<T> success(T data, String msg) {
        return new ApiResponseVO<>(200, msg, data);
    }
    
    /**
     * 失败响应
     */
    public static <T> ApiResponseVO<T> fail(int code, String msg) {
        return new ApiResponseVO<>(code, msg, null);
    }
    
    /**
     * 失败响应（默认状态码）
     */
    public static <T> ApiResponseVO<T> fail(String msg) {
        return new ApiResponseVO<>(500, msg, null);
    }
}
