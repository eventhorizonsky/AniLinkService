package xyz.ezsky.anilink.listener;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import xyz.ezsky.anilink.service.MediaScannerService;

@Component
@Log4j2
public class StartupScanListener implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    private MediaScannerService mediaScannerService;

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        log.info("Application is ready. Starting initial library scan...");
        mediaScannerService.scanAllLibraries();
    }
}
