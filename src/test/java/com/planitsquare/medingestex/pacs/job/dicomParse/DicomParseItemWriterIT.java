package com.planitsquare.medingestex.pacs.job.dicomParse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.planitsquare.medingestex.pacs.domain.DicomRecord;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DicomParseItemWriterIT {

    @Autowired
    private DicomParseItemWriter writer;

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
                VALUES (1, '/test/path/study1', 'CT', 'SCANNED'),
                       (2, '/test/path/study2', 'MR', 'SCANNED')
                """);
    }

    @Test
    void write_singleRecord_insertsAndUpdatesStatus() throws Exception {
        DicomRecord record = buildRecord(1L, "PT001", "/test/path/study1");

        writer.write(new Chunk<>(record));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dicom_records WHERE dicom_study_directory_id = 1", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT scan_status FROM dicom_study_directory WHERE id = 1", String.class))
                .isEqualTo("PARSED");
    }

    @Test
    void write_multipleRecords_insertsAll() throws Exception {
        DicomRecord r1 = buildRecord(1L, "PT001", "/test/path/study1");
        DicomRecord r2 = buildRecord(2L, "PT002", "/test/path/study2");

        writer.write(new Chunk<>(r1, r2));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dicom_records", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void write_duplicate_noError() throws Exception {
        DicomRecord record = buildRecord(1L, "PT001", "/test/path/study1");

        writer.write(new Chunk<>(record));
        assertThatCode(() -> writer.write(new Chunk<>(record))).doesNotThrowAnyException();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dicom_records WHERE dicom_study_directory_id = 1", Integer.class))
                .isEqualTo(1);
    }

    private DicomRecord buildRecord(Long dirId, String ptNo, String filePath) {
        return DicomRecord.builder()
                .dicomStudyDirectoryId(dirId)
                .ptNo(ptNo)
                .filePath(filePath)
                .acquisitionDatetime(LocalDateTime.of(2024, 3, 15, 10, 30, 0))
                .studyUid("1.2.3.5")
                .seriesUid("1.2.3.6")
                .bodyPart("CHEST")
                .modality("CT")
                .patientPosition("HFS")
                .build();
    }
}