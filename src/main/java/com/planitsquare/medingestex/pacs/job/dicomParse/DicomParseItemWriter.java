package com.planitsquare.medingestex.pacs.job.dicomParse;

import com.planitsquare.medingestex.pacs.domain.DicomRecord;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DicomParseItemWriter implements ItemWriter<DicomRecord> {

    private static final String INSERT_SQL = """
            INSERT INTO dicom_records
                (dicom_study_directory_id, pt_no, file_path, acquisition_datetime,
                 study_uid, series_uid, body_part, modality, patient_position)
            VALUES
                (:dicomStudyDirectoryId, :ptNo, :filePath, :acquisitionDatetime,
                 :studyUid, :seriesUid, :bodyPart, :modality, :patientPosition)
            ON CONFLICT (dicom_study_directory_id) DO NOTHING
            """;

    private static final String UPDATE_STATUS_SQL = """
            UPDATE dicom_study_directory SET scan_status = 'PARSED', updated_at = NOW()
            WHERE id = :dicomStudyDirectoryId
            """;

    private final DataSource dataSource;

    @Override
    public void write(Chunk<? extends DicomRecord> chunk) {
        NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
        BeanPropertySqlParameterSource[] params = chunk.getItems().stream()
                .map(BeanPropertySqlParameterSource::new)
                .toArray(BeanPropertySqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(INSERT_SQL, params);
        jdbcTemplate.batchUpdate(UPDATE_STATUS_SQL, params);
    }
}
