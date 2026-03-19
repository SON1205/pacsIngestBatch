package com.planitsquare.medingestex.pacs.job;

import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectory;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StudyDirectoryItemWriter implements ItemWriter<DicomStudyDirectory> {

    private static final String INSERT_SQL = """
            WITH ins AS (
                INSERT INTO dicom_study_directory (full_path, modality, scan_status)
                VALUES (:fullPath, :modality, :scanStatus)
                ON CONFLICT (full_path) DO NOTHING
                RETURNING id
            )
            INSERT INTO dicom_study_directory_detail
                (id, root_folder, sub_folder, study_date, dir_name, patient_id, exam_date, exam_time, accession_number)
            SELECT id, :detail.rootFolder, :detail.subFolder, :detail.studyDate, :detail.dirName,
                   :detail.patientId, :detail.examDate, :detail.examTime, :detail.accessionNumber
            FROM ins
            """;

    private final DataSource dataSource;

    @Override
    public void write(Chunk<? extends DicomStudyDirectory> chunk) {
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        BeanPropertySqlParameterSource[] params = chunk.getItems().stream()
                .map(BeanPropertySqlParameterSource::new)
                .toArray(BeanPropertySqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(INSERT_SQL, params);
    }
}
