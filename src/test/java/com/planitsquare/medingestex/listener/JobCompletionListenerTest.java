package com.planitsquare.medingestex.listener;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.junit.jupiter.api.Test;

class JobCompletionListenerTest {

    private final JobCompletionListener listener = new JobCompletionListener();

    private static JobExecution createJobExecution(BatchStatus status) {
        JobExecution execution = new JobExecution(1L, new JobInstance(1L, "testJob"), new JobParameters());
        execution.setStatus(status);
        return execution;
    }

    @Test
    void beforeJob_logsJobName() {
        assertThatCode(() -> listener.beforeJob(createJobExecution(BatchStatus.STARTING)))
                .doesNotThrowAnyException();
    }

    @Test
    void afterJob_completed_doesNotThrow() {
        assertThatCode(() -> listener.afterJob(createJobExecution(BatchStatus.COMPLETED)))
                .doesNotThrowAnyException();
    }

    @Test
    void afterJob_failed_logsWarningAndExceptions() {
        JobExecution execution = createJobExecution(BatchStatus.FAILED);
        execution.addFailureException(new RuntimeException("test error"));

        assertThatCode(() -> listener.afterJob(execution)).doesNotThrowAnyException();
    }

    @Test
    void afterJob_stopped_logsWarning() {
        assertThatCode(() -> listener.afterJob(createJobExecution(BatchStatus.STOPPED)))
                .doesNotThrowAnyException();
    }
}