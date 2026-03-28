package xyz.ezsky.anilink.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import xyz.ezsky.anilink.model.vo.ApiResponseVO;

@RestControllerAdvice
@Log4j2
public class ApiResponseLoggingAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (!(body instanceof ApiResponseVO<?> apiResponse)) {
            return body;
        }

        int code = apiResponse.getCode();
        if (code < 400) {
            return body;
        }

        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String message = apiResponse.getMsg();

        if (code == 401 || code == 403) {
            log.info("API authorization failure: method={}, path={}, code={}, msg={}", method, path, code, message);
        } else if (code >= 500) {
            log.error("API responded with error: method={}, path={}, code={}, msg={}", method, path, code, message);
        } else {
            log.warn("API responded with client error: method={}, path={}, code={}, msg={}", method, path, code, message);
        }

        return body;
    }
}
