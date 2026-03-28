package xyz.ezsky.anilink.config;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.hutool.http.HttpStatus;
import lombok.extern.log4j.Log4j2;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

@RestControllerAdvice
@Log4j2
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponseVO<String>> handleUnauthorizedException(NotLoginException e) {
        log.warn("Unauthorized request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.HTTP_UNAUTHORIZED)
                .body(ApiResponseVO.fail(HttpStatus.HTTP_UNAUTHORIZED, e.getMessage()));
    }

    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<ApiResponseVO<String>> handleForbiddenException(NotRoleException e) {
        log.warn("Forbidden request: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.HTTP_FORBIDDEN)
                .body(ApiResponseVO.fail(HttpStatus.HTTP_FORBIDDEN, e.getMessage()));
    }

    @ExceptionHandler({
            AsyncRequestNotUsableException.class,
            ClientAbortException.class
    })
    public void handleClientDisconnect(Exception e) {
        if (isClientDisconnect(e)) {
            log.debug("Client disconnected while writing response: {}", rootMessage(e));
            return;
        }
        log.warn("Skipped non-recoverable response write exception: {}", rootMessage(e));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponseVO<String>> handleRuntimeException(RuntimeException e) {
        log.error("Unhandled runtime exception", e);
        return ResponseEntity.status(HttpStatus.HTTP_BAD_REQUEST)
                .body(ApiResponseVO.fail(HttpStatus.HTTP_BAD_REQUEST, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseVO<String>> handleException(Exception e) {
        if (isClientDisconnect(e)) {
            log.debug("Client disconnected while handling exception: {}", rootMessage(e));
            return ResponseEntity.status(408).build();
        }
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.HTTP_INTERNAL_ERROR)
                .body(ApiResponseVO.fail(HttpStatus.HTTP_INTERNAL_ERROR, e.getMessage()));
    }

    private boolean isClientDisconnect(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AsyncRequestNotUsableException || current instanceof ClientAbortException) {
                return true;
            }
            if (current instanceof SocketTimeoutException || current instanceof SocketException || current instanceof IOException) {
                String message = current.getMessage();
                if (message != null) {
                    String normalized = message.toLowerCase();
                    if (normalized.contains("broken pipe")
                            || normalized.contains("connection reset by peer")
                            || normalized.contains("connection reset")
                            || normalized.contains("socket closed")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : current.getClass().getSimpleName();
    }
}
