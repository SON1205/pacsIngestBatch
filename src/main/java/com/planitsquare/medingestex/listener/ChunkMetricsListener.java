package com.planitsquare.medingestex.listener;

import java.time.Duration;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChunkMetricsListener implements ChunkListener<Object, Object>, StepExecutionListener {

    private final ThreadLocal<StepExecution> stepExecutionHolder = new ThreadLocal<>();

    @Override
    public void beforeStep(StepExecution stepExecution) {
        stepExecutionHolder.set(stepExecution);
    }

    @Override
    public void afterChunk(Chunk<Object> chunk) {
        StepExecution step = stepExecutionHolder.get();
        if (step == null || step.getStartTime() == null) {
            return;
        }

        Duration elapsed = Duration.between(step.getStartTime(), Instant.now());
        long written = step.getWriteCount();
        long skipped = step.getSkipCount();
        double throughput = elapsed.toSeconds() > 0 ? (double) written / elapsed.toSeconds() : 0;

        log.info("[{}] written: {}, skipped: {}, elapsed: {}s, throughput: {}/s",
                step.getStepName(), written, skipped, elapsed.toSeconds(), String.format("%.1f", throughput));
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        stepExecutionHolder.remove();
        return null;
    }
}
