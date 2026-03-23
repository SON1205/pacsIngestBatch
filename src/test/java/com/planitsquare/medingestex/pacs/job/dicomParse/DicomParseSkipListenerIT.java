package com.planitsquare.medingestex.pacs.job.dicomParse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.planitsquare.medingestex.pacs.domain.DicomRecord;
import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectoryRow;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DicomParseSkipListenerIT {

    @Autowired
    private DicomParseSkipListener skipListener;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM dicom_records");
        jdbcTemplate.update("DELETE FROM dicom_study_directory_detail");
        jdbcTemplate.update("DELETE FROM dicom_study_directory");

        jdbcTemplate.update("""
                INSERT INTO dicom_study_directory (id, full_path, modality, scan_status)
                VALUES (1, '/test/path/study1', 'CT', 'SCANNED')
                """);
    }

    @Test
    void onSkipInProcess_updatesStatusToParseFailed() {
        DicomStudyDirectoryRow row = new DicomStudyDirectoryRow(1L, "/test/path/study1", "CT");

        skipListener.onSkipInProcess(row, new RuntimeException("parse error"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT scan_status FROM dicom_study_directory WHERE id = 1", String.class))
                .isEqualTo("PARSE_FAILED");
    }

    @Test
    void onSkipInRead_doesNotThrow() {
        assertThatCode(() -> skipListener.onSkipInRead(new RuntimeException("read error")))
                .doesNotThrowAnyException();
    }

    @Test
    void onSkipInWrite_doesNotThrow() {
        DicomRecord record = DicomRecord.builder()
                .dicomStudyDirectoryId(1L)
                .ptNo("PT001")
                .filePath("/test/path")
                .acquisitionDatetime(java.time.LocalDateTime.now())
                .build();

        assertThatCode(() -> skipListener.onSkipInWrite(record, new RuntimeException("write error")))
                .doesNotThrowAnyException();
    }
}