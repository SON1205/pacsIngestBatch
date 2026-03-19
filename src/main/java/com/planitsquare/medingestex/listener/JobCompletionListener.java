package com.planitsquare.medingestex.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JobCompletionListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("Job [{}] started", jobExecution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();

        if (status == BatchStatus.COMPLETED) {
            log.info("Job [{}] completed successfully", jobName);
        } else {
            log.warn("Job [{}] finished with status: {}", jobName, status);
            jobExecution.getAllFailureExceptions().forEach(ex ->
                    log.error("  Failure: {}", ex.getMessage()));
        }
    }
}
