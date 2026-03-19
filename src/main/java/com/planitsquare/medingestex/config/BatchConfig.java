package com.planitsquare.medingestex.config;

import com.planitsquare.medingestex.listener.JobCompletionListener;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Getter
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(PacsProperties.class)
public class BatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager txManager;
    private final DataSource dataSource;
    private final JobCompletionListener jobCompletionListener;
}