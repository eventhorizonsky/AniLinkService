package xyz.ezsky.anilink.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.hutool.http.HttpStatus;
import lombok.extern.log4j.Log4j2;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;

@RestControllerAdvice
@Log4j2
public class GlobalExceptionHandler {
    
    // 处理鉴权异常
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponseVO<String>> handleUnauthorizedException(NotLoginException e) {
        log.warn("Unauthorized request", e);
        return ResponseEntity.status(HttpStatus.HTTP_UNAUTHORIZED)
                .body(ApiResponseVO.fail(e.getMessage()));
    }

    // 处理权限不足异常
    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<ApiResponseVO<String>> handleForbiddenException(NotRoleException e) {
        log.warn("Forbidden request", e);
        return ResponseEntity.status(HttpStatus.HTTP_FORBIDDEN)
                .body(ApiResponseVO.fail(HttpStatus.HTTP_FORBIDDEN, e.getMessage()));
    }
    
    // 处理运行时异常
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponseVO<String>> handleRuntimeException(RuntimeException e) {
        log.error("Unhandled runtime exception", e);
        return ResponseEntity.status(HttpStatus.HTTP_BAD_REQUEST)
                .body(ApiResponseVO.fail(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseVO<String>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.HTTP_INTERNAL_ERROR)
                .body(ApiResponseVO.fail(HttpStatus.HTTP_INTERNAL_ERROR, e.getMessage()));
    }
}
