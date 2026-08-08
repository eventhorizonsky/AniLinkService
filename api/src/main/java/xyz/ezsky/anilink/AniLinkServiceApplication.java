package xyz.ezsky.anilink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableRetry
@EnableScheduling
@SpringBootApplication
public class AniLinkServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AniLinkServiceApplication.class, args);
	}

}
