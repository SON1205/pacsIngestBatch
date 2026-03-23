package com.planitsquare.medingestex.pacs.job.dicomParse;

import com.planitsquare.medingestex.pacs.domain.DicomRecord;
import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectoryRow;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DicomParseSkipListener implements SkipListener<DicomStudyDirectoryRow, DicomRecord> {

    private static final String UPDATE_FAILED_SQL = """
            UPDATE dicom_study_directory SET scan_status = 'PARSE_FAILED', updated_at = NOW()
            WHERE id = :id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DicomParseSkipListener(DataSource dataSource) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("Skipped during READ: {}", t.getMessage(), t);
    }

    @Override
    public void onSkipInProcess(DicomStudyDirectoryRow row, Throwable t) {
        log.warn("Skipped during PROCESS [id={}, path={}]: {}", row.id(), row.fullPath(), t.getMessage(), t);
        updateStatus(row.id());
    }

    @Override
    public void onSkipInWrite(DicomRecord item, Throwable t) {
        log.warn("Skipped during WRITE [dicomStudyDirectoryId={}]: {}", item.getDicomStudyDirectoryId(), t.getMessage(),
                t);
    }

    private void updateStatus(Long id) {
        try {
            jdbcTemplate.update(UPDATE_FAILED_SQL, new MapSqlParameterSource("id", id));
        } catch (Exception e) {
            log.error("Failed to update scan_status to PARSE_FAILED for id={}: {}", id, e.getMessage());
        }
    }
}
