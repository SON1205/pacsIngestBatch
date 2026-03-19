package com.planitsquare.medingestex.listener;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StepMetricsListener implements StepExecutionListener {

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("Step [{}] started", stepExecution.getStepName());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        Duration duration = Duration.between(stepExecution.getStartTime(), stepExecution.getEndTime());
        long readCount = stepExecution.getReadCount();
        double readsPerSec = duration.toSeconds() > 0
                ? (double) readCount / duration.toSeconds()
                : 0;

        log.info("Step [{}] — status: {}, read: {}, written: {}, skipped: {}, duration: {}s, throughput: {} reads/s",
                stepExecution.getStepName(), stepExecution.getStatus(), readCount, stepExecution.getWriteCount(),
                stepExecution.getSkipCount(), duration.toSeconds(), String.format("%.1f", readsPerSec));

        return stepExecution.getExitStatus();
    }
}