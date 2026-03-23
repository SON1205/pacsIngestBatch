package com.planitsquare.medingestex.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDateTime;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import org.junit.jupiter.api.Test;

class StepMetricsListenerTest {

    private final StepMetricsListener listener = new StepMetricsListener();

    private static JobExecution createJobExecution() {
        return new JobExecution(1L, new JobInstance(1L, "testJob"), new JobParameters());
    }

    @Test
    void beforeStep_doesNotThrow() {
        StepExecution step = new StepExecution("testStep", createJobExecution());
        assertThatCode(() -> listener.beforeStep(step)).doesNotThrowAnyException();
    }

    @Test
    void afterStep_returnsOriginalExitStatus() {
        StepExecution step = new StepExecution("testStep", createJobExecution());
        step.setStartTime(LocalDateTime.now().minusSeconds(5));
        step.setEndTime(LocalDateTime.now());
        step.setReadCount(100);
        step.setWriteCount(95);
        step.setExitStatus(ExitStatus.COMPLETED);

        ExitStatus result = listener.afterStep(step);

        assertThat(result).isEqualTo(ExitStatus.COMPLETED);
    }

    @Test
    void afterStep_withZeroDuration_doesNotThrow() {
        StepExecution step = new StepExecution("testStep", createJobExecution());
        LocalDateTime now = LocalDateTime.now();
        step.setStartTime(now);
        step.setEndTime(now);
        step.setExitStatus(ExitStatus.COMPLETED);

        assertThatCode(() -> listener.afterStep(step)).doesNotThrowAnyException();
    }
}