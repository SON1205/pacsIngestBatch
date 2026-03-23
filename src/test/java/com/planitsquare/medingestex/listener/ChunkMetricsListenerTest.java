package com.planitsquare.medingestex.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;

class ChunkMetricsListenerTest {

    private final ChunkMetricsListener listener = new ChunkMetricsListener();

    private static JobExecution createJobExecution() {
        return new JobExecution(1L, new JobInstance(1L, "testJob"), new JobParameters());
    }

    @Test
    void afterChunk_withStepExecution_doesNotThrow() {
        StepExecution step = new StepExecution("testStep", createJobExecution());
        step.setStartTime(LocalDateTime.now().minusSeconds(10));
        step.setWriteCount(100);

        listener.beforeStep(step);

        assertThatCode(() -> listener.afterChunk(new Chunk<>()))
                .doesNotThrowAnyException();
    }

    @Test
    void afterChunk_withoutBeforeStep_doesNotThrow() {
        assertThatCode(() -> listener.afterChunk(new Chunk<>()))
                .doesNotThrowAnyException();
    }

    @Test
    void afterChunk_withNullStartTime_doesNotThrow() {
        StepExecution step = new StepExecution("testStep", createJobExecution());

        listener.beforeStep(step);

        assertThatCode(() -> listener.afterChunk(new Chunk<>()))
                .doesNotThrowAnyException();
    }

    @Test
    void afterStep_cleansUpThreadLocal() {
        StepExecution step = new StepExecution("testStep", createJobExecution());
        step.setStartTime(LocalDateTime.now());

        listener.beforeStep(step);
        listener.afterStep(step);

        // afterChunk should not throw even after cleanup
        assertThatCode(() -> listener.afterChunk(new Chunk<>()))
                .doesNotThrowAnyException();
    }

    @Test
    void afterStep_returnsNull() {
        StepExecution step = new StepExecution("testStep", createJobExecution());
        assertThat(listener.afterStep(step)).isNull();
    }
}