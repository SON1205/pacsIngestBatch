package com.planitsquare.medingestex.pacs.job.dicomParse;

import com.planitsquare.medingestex.config.BatchConfig;
import com.planitsquare.medingestex.config.PacsProperties;
import com.planitsquare.medingestex.config.PacsProperties.DicomParse;
import com.planitsquare.medingestex.listener.ChunkMetricsListener;
import com.planitsquare.medingestex.listener.StepMetricsListener;
import com.planitsquare.medingestex.pacs.domain.DicomRecord;
import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectoryRow;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@RequiredArgsConstructor
public class DicomParseJobConfig {

    private final BatchConfig batch;
    private final PacsProperties pacsProperties;
    private final StepMetricsListener stepMetricsListener;
    private final DicomParseSkipListener dicomParseSkipListener;
    private final ChunkMetricsListener chunkMetricsListener;
    private final DicomParseItemWriter dicomParseItemWriter;
    private final DicomParseVerifyTasklet dicomParseVerifyTasklet;

    @Bean
    public Job dicomParseJob(Step dicomParsePartitionStep, Step dicomParseVerifyStep) {
        return new JobBuilder("dicomParseJob", batch.getJobRepository())
                .listener(batch.getJobCompletionListener())
                .start(dicomParsePartitionStep)
                .next(dicomParseVerifyStep)
                .build();
    }

    @Bean
    public Step dicomParseVerifyStep() {
        return new StepBuilder("dicomParseVerifyStep", batch.getJobRepository())
                .tasklet(dicomParseVerifyTasklet)
                .transactionManager(batch.getTxManager())
                .build();
    }

    @Bean
    public Step dicomParsePartitionStep(Step dicomParseStep) {
        DicomParse config = pacsProperties.getDicomParse();
        return new StepBuilder("dicomParsePartitionStep", batch.getJobRepository())
                .partitioner("dicomParseStep", dicomParsePartitioner())
                .step(dicomParseStep)
                .gridSize(config.getGridSize())
                .taskExecutor(dicomParseExecutor())
                .build();
    }

    @Bean
    public Step dicomParseStep(JdbcPagingItemReader<DicomStudyDirectoryRow> dicomParseReader,
                               DicomParseItemProcessor dicomParseProcessor) {
        DicomParse config = pacsProperties.getDicomParse();
        return new StepBuilder("dicomParseStep", batch.getJobRepository())
                .<DicomStudyDirectoryRow, DicomRecord>chunk(config.getChunkSize())
                .transactionManager(batch.getTxManager())
                .reader(dicomParseReader)
                .processor(dicomParseProcessor)
                .writer(dicomParseItemWriter)
                .listener(stepMetricsListener)
                .listener(chunkMetricsListener)
                .listener(dicomParseSkipListener)
                .faultTolerant()
                .skip(IOException.class, UncheckedIOException.class, SecurityException.class)
                .skip(IllegalStateException.class)
                .skipLimit(config.getSkipLimit())
                .build();
    }

    @Bean
    @StepScope
    public JdbcPagingItemReader<DicomStudyDirectoryRow> dicomParseReader(
            @Value("#{stepExecutionContext['modalities']}") String modalities,
            @Value("#{stepExecutionContext['minId']}") Long minId,
            @Value("#{stepExecutionContext['maxId']}") Long maxId) throws Exception {
        PacsProperties.DicomParse config = pacsProperties.getDicomParse();

        String[] mods = modalities.split(",");
        StringBuilder inClause = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < mods.length; i++) {
            if (i > 0) inClause.append(", ");
            String paramName = "mod" + i;
            inClause.append(":").append(paramName);
            params.put(paramName, mods[i]);
        }
        params.put("minId", minId);
        params.put("maxId", maxId);

        return new JdbcPagingItemReaderBuilder<DicomStudyDirectoryRow>()
                .name("dicomParseReader")
                .dataSource(batch.getDataSource())
                .selectClause("SELECT id, full_path, modality")
                .fromClause("FROM dicom_study_directory")
                .whereClause("WHERE modality IN (" + inClause + ") AND id BETWEEN :minId AND :maxId AND scan_status = 'SCANNED'")
                .sortKeys(Map.of("id", Order.ASCENDING))
                .parameterValues(params)
                .rowMapper(new DataClassRowMapper<>(DicomStudyDirectoryRow.class))
                .pageSize(config.getChunkSize())
                .build();
    }

    @Bean
    @StepScope
    public DicomParseItemProcessor dicomParseProcessor() {
        return new DicomParseItemProcessor();
    }

    @Bean
    public Partitioner dicomParsePartitioner() {
        return new ModalityPartitioner(batch.getDataSource());
    }

    @Bean
    public TaskExecutor dicomParseExecutor() {
        DicomParse config = pacsProperties.getDicomParse();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(config.getThreadCount());
        executor.setMaxPoolSize(config.getThreadCount());
        executor.setThreadNamePrefix("dicom-parse-");
        return executor;
    }
}