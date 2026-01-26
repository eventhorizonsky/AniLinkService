package xyz.ezsky.anilink.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;

import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "AniLinkService API 文档", version = "1.0.0", description = "基于弹弹play开放平台搭建的番剧识别与弹幕播放器"), servers = @Server(url = "/", description = "默认服务器地址"))
public class OpenApiConfig {

}
