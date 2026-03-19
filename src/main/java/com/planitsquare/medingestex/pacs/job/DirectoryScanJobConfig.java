package com.planitsquare.medingestex.pacs.job;

import com.planitsquare.medingestex.config.BatchConfig;
import com.planitsquare.medingestex.config.PacsProperties;
import com.planitsquare.medingestex.listener.SkipLoggingListener;
import com.planitsquare.medingestex.listener.StepMetricsListener;
import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class DirectoryScanJobConfig {

    private final BatchConfig batch;
    private final PacsProperties pacsProperties;
    private final StepMetricsListener stepMetricsListener;
    private final SkipLoggingListener skipLoggingListener;

    @Bean
    public Job directoryScanJob(Step directoryScanStep) {
        return new JobBuilder("directoryScanJob", batch.getJobRepository())
                .listener(batch.getJobCompletionListener())
                .start(directoryScanStep)
                .build();
    }

    @Bean
    public Step directoryScanStep(StudyDirectoryItemReader reader,
                                  StudyDirectoryItemProcessor processor,
                                  StudyDirectoryItemWriter writer) {
        return new StepBuilder("directoryScanStep", batch.getJobRepository())
                .<Path, DicomStudyDirectory>chunk(pacsProperties.getChunkSize())
                .transactionManager(batch.getTxManager())
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .listener(stepMetricsListener)
                .listener(skipLoggingListener)
                .faultTolerant()
                .retry(TransientDataAccessException.class)
                .retryLimit(pacsProperties.getRetryLimit())
                .skip(IOException.class, UncheckedIOException.class, SecurityException.class)
                .skip(TransientDataAccessException.class)
                .skipLimit(pacsProperties.getSkipLimit())
                .build();
    }
}