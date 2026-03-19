package com.planitsquare.medingestex.listener;

import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Slf4j
@Component
public class SkipLoggingListener implements SkipListener<Path, DicomStudyDirectory> {

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("Skipped during READ: {}", t.getMessage(), t);
    }

    @Override
    public void onSkipInProcess(Path path, Throwable t) {
        log.warn("Skipped during PROCESS [{}]: {}", path, t.getMessage(), t);
    }

    @Override
    public void onSkipInWrite(DicomStudyDirectory item, Throwable t) {
        log.warn("Skipped during WRITE [{}]: {}", item.getFullPath(), t.getMessage(), t);
    }
}