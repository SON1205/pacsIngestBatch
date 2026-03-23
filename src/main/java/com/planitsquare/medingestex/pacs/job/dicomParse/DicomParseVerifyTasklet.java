package com.planitsquare.medingestex.pacs.job.dicomParse;

import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DicomParseVerifyTasklet implements Tasklet {

    private final DataSource dataSource;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        long parsed = count(jdbc, "PARSED");
        long failed = count(jdbc, "PARSE_FAILED");
        long remaining = count(jdbc, "SCANNED");
        long orphaned = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dicom_study_directory d
                LEFT JOIN dicom_records r ON d.id = r.dicom_study_directory_id
                WHERE d.scan_status = 'PARSED' AND r.dicom_record_id IS NULL
                """, Long.class);
        long nullPtNo = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dicom_records WHERE pt_no IS NULL OR pt_no = ''", Long.class);

        log.info("=== Verification Result ===");
        log.info("PARSED: {}, PARSE_FAILED: {}, SCANNED(remaining): {}", parsed, failed, remaining);
        log.info("Orphaned (PARSED but no record): {}", orphaned);
        log.info("Null/empty pt_no: {}", nullPtNo);

        boolean hasErrors = false;
        if (orphaned > 0) {
            log.error("Integrity error: {} directories marked PARSED but missing dicom_records", orphaned);
            hasErrors = true;
        }
        if (nullPtNo > 0) {
            log.error("Data quality error: {} records with null/empty pt_no", nullPtNo);
            hasErrors = true;
        }
        if (remaining > 0) {
            log.warn("Unprocessed: {} directories still in SCANNED status", remaining);
        }

        if (hasErrors) {
            throw new IllegalStateException("Verification failed — check logs for details");
        }

        log.info("Verification passed");
        return RepeatStatus.FINISHED;
    }

    private long count(JdbcTemplate jdbc, String status) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM dicom_study_directory WHERE scan_status = ?", Long.class, status);
    }
}