package xyz.ezsky.anilink.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cn.dev33.satoken.exception.NotLoginException;
import cn.hutool.http.HttpStatus;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;

@RestControllerAdvice
public class GlobalExceptionHandler {
    
    // 处理鉴权异常
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponseVO<String>> handleUnauthorizedException(NotLoginException e) {
        return ResponseEntity.status(HttpStatus.HTTP_UNAUTHORIZED)
                .body(ApiResponseVO.fail(e.getMessage()));
    }
    
    // 处理运行时异常
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponseVO<String>> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.HTTP_BAD_REQUEST)
                .body(ApiResponseVO.fail(e.getMessage()));
    }
}
