package com.planitsquare.medingestex.pacs.job;

import com.planitsquare.medingestex.config.PacsProperties;
import com.planitsquare.medingestex.config.TestBatchConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestBatchConfig.class)
class DirectoryScanJobIT {

    @Autowired
    private JobOperatorTestUtils jobOperatorTestUtils;

    @Autowired
    private JobRepositoryTestUtils jobRepositoryTestUtils;

    @Autowired
    private PacsProperties pacsProperties;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM dicom_study_directory_detail");
        jdbcTemplate.update("DELETE FROM dicom_study_directory");
        jobRepositoryTestUtils.removeJobExecutions();
    }

    @Test
    void endToEnd_scansDirectoriesAndPersists(@TempDir Path tempDir) throws Exception {
        pacsProperties.setRootPath(tempDir.toString());
        pacsProperties.setRootFolders(java.util.List.of("Midterm"));
        pacsProperties.setSubFolderPattern(null);

        Files.createDirectories(tempDir.resolve("Midterm/Sub001/20240101/CT/PT001_20240101_120000_ACC123_CT"));
        Files.createDirectories(tempDir.resolve("Midterm/Sub001/20240101/MR/PT002_20240102_130000_ACC456_MR"));
        Files.createDirectories(tempDir.resolve("Midterm/Sub001/20240102/CT/PT003_20240103_140000_ACC789_CT"));

        JobExecution execution = jobOperatorTestUtils.startJob();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getReadCount()).isEqualTo(3);
        assertThat(stepExecution.getWriteCount()).isEqualTo(3);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dicom_study_directory", Integer.class))
                .isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dicom_study_directory_detail", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void emptyDirectory_completesWithZeroReads(@TempDir Path tempDir) throws Exception {
        pacsProperties.setRootPath(tempDir.toString());
        pacsProperties.setRootFolders(java.util.List.of("Midterm"));
        pacsProperties.setSubFolderPattern(null);

        Files.createDirectories(tempDir.resolve("Midterm"));

        JobExecution execution = jobOperatorTestUtils.startJob();

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        StepExecution stepExecution = execution.getStepExecutions().iterator().next();
        assertThat(stepExecution.getReadCount()).isZero();
    }

    @Test
    void idempotency_secondRunProducesNoDuplicates(@TempDir Path tempDir) throws Exception {
        pacsProperties.setRootPath(tempDir.toString());
        pacsProperties.setRootFolders(java.util.List.of("Midterm"));
        pacsProperties.setSubFolderPattern(null);

        Files.createDirectories(tempDir.resolve("Midterm/Sub001/20240101/CT/PT001_20240101_120000_ACC123_CT"));

        jobOperatorTestUtils.startJob();

        // Second run with unique job parameters
        JobExecution execution2 = jobOperatorTestUtils.startJob(
                jobOperatorTestUtils.getUniqueJobParameters());

        assertThat(execution2.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dicom_study_directory", Integer.class))
                .isEqualTo(1);
    }
}