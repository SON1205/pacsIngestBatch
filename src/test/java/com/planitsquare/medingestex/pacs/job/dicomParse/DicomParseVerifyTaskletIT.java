package com.planitsquare.medingestex.pacs.job.dicomParse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DicomParseVerifyTaskletIT {

    @Autowired
    private DicomParseVerifyTasklet tasklet;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM dicom_records");
        jdbcTemplate.update("DELETE FROM dicom_study_directory_detail");
        jdbcTemplate.update("DELETE FROM dicom_study_directory");
    }

    @Test
    void execute_allParsedWithRecords_passes() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO dicom_study_directory (id, full_path, modality, scan_status)
                VALUES (1, '/path/1', 'CT', 'PARSED')
                """);
        jdbcTemplate.update("""
                INSERT INTO dicom_records (dicom_study_directory_id, pt_no, file_path, acquisition_datetime)
                VALUES (1, 'PT001', '/path/1', '2024-03-15 10:30:00')
                """);

        RepeatStatus result = tasklet.execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    void execute_orphanedRecord_throwsException() {
        jdbcTemplate.update("""
                INSERT INTO dicom_study_directory (id, full_path, modality, scan_status)
                VALUES (1, '/path/1', 'CT', 'PARSED')
                """);
        // No dicom_records inserted → orphaned

        assertThatThrownBy(() -> tasklet.execute(null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Verification failed");
    }

    @Test
    void execute_emptyTables_passes() throws Exception {
        RepeatStatus result = tasklet.execute(null, null);
        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    void execute_withParseFailed_passes() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO dicom_study_directory (id, full_path, modality, scan_status)
                VALUES (1, '/path/1', 'CT', 'PARSE_FAILED')
                """);

        RepeatStatus result = tasklet.execute(null, null);
        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
    }
}