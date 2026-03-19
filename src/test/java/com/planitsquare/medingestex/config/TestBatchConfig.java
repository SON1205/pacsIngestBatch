package com.planitsquare.medingestex.config;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestBatchConfig {

    @Bean
    public JobOperatorTestUtils jobOperatorTestUtils(Job job, JobOperator jobOperator,
                                                     JobRepository jobRepository) {
        JobOperatorTestUtils utils = new JobOperatorTestUtils(jobOperator, jobRepository);
        utils.setJob(job);
        return utils;
    }

    @Bean
    public JobRepositoryTestUtils jobRepositoryTestUtils(JobRepository jobRepository) {
        return new JobRepositoryTestUtils(jobRepository);
    }
}