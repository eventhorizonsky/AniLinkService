package xyz.ezsky.anilink.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录请求")
public class LoginRequest {
    
    @Schema(description = "用户名", example = "admin")
    private String username;
    
    @Schema(description = "密码", example = "123456")
    private String password;
}
