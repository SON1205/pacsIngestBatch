package com.planitsquare.medingestex.pacs.job.directoryScan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectory;
import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectoryDetail;
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
class StudyDirectoryItemWriterIT {

    @Autowired
    private StudyDirectoryItemWriter writer;

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
    void write_singleItem_insertsIntoBothTables() throws Exception {
        DicomStudyDirectory item = createStudyDirectory("/test/path/study1", "CT");

        writer.write(new Chunk<>(item));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dicom_study_directory WHERE full_path = '/test/path/study1'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dicom_study_directory_detail WHERE dir_name = 'PT001_20240101_120000_ACC123_CT'",
                Integer.class))
                .isEqualTo(1);
    }

    @Test
    void write_multipleItems_insertsAll() throws Exception {
        DicomStudyDirectory item1 = createStudyDirectory("/test/path/study1", "CT");
        DicomStudyDirectory item2 = createStudyDirectory("/test/path/study2", "MR");

        writer.write(new Chunk<>(item1, item2));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dicom_study_directory", Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dicom_study_directory_detail", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void write_duplicateFullPath_noErrorOnConflict() throws Exception {
        DicomStudyDirectory item = createStudyDirectory("/test/path/duplicate", "CT");

        writer.write(new Chunk<>(item));

        assertThatCode(() -> writer.write(new Chunk<>(item))).doesNotThrowAnyException();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM dicom_study_directory WHERE full_path = '/test/path/duplicate'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void write_nullDetailFields_insertsWithNulls() throws Exception {
        DicomStudyDirectoryDetail detail = DicomStudyDirectoryDetail.fromDirName(
                "shortname", "Midterm", "Midterm001", "20240101");
        DicomStudyDirectory item = DicomStudyDirectory.of("/test/path/nullfields", "CT", detail);

        assertThatCode(() -> writer.write(new Chunk<>(item))).doesNotThrowAnyException();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT patient_id FROM dicom_study_directory_detail WHERE dir_name = 'shortname'", String.class))
                .isNull();
    }

    private DicomStudyDirectory createStudyDirectory(String fullPath, String modality) {
        DicomStudyDirectoryDetail detail = DicomStudyDirectoryDetail.fromDirName(
                "PT001_20240101_120000_ACC123_CT", "Midterm", "Midterm001", "20240101");
        return DicomStudyDirectory.of(fullPath, modality, detail);
    }
}
