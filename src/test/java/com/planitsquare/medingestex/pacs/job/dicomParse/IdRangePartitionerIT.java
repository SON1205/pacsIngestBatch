package com.planitsquare.medingestex.pacs.job.dicomParse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class IdRangePartitionerIT {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbcTemplate;
    private IdRangePartitioner partitioner;

    @BeforeEach
    void setUp() {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("DELETE FROM dicom_records");
        jdbcTemplate.update("DELETE FROM dicom_study_directory_detail");
        jdbcTemplate.update("DELETE FROM dicom_study_directory");
        partitioner = new IdRangePartitioner(dataSource);
    }

    @Test
    void partition_withData_createsPartitions() {
        jdbcTemplate.update("""
                INSERT INTO dicom_study_directory (id, full_path, modality, scan_status)
                VALUES (1, '/path/1', 'CT', 'SCANNED'),
                       (2, '/path/2', 'MR', 'SCANNED'),
                       (3, '/path/3', 'CT', 'SCANNED'),
                       (4, '/path/4', 'US', 'SCANNED')
                """);

        Map<String, ExecutionContext> result = partitioner.partition(2);

        assertThat(result).isNotEmpty();
        assertThat(result.values()).allSatisfy(ctx -> {
            assertThat(ctx.getLong("minId")).isGreaterThan(0);
            assertThat(ctx.getLong("maxId")).isGreaterThanOrEqualTo(ctx.getLong("minId"));
        });
    }

    @Test
    void partition_emptyTable_returnsEmpty() {
        Map<String, ExecutionContext> result = partitioner.partition(4);
        assertThat(result).isEmpty();
    }

    @Test
    void partition_onlyParsedRows_returnsEmpty() {
        jdbcTemplate.update("""
                INSERT INTO dicom_study_directory (id, full_path, modality, scan_status)
                VALUES (1, '/path/1', 'CT', 'PARSED')
                """);

        Map<String, ExecutionContext> result = partitioner.partition(2);
        assertThat(result).isEmpty();
    }

    @Test
    void partition_singleRow_createsSinglePartition() {
        jdbcTemplate.update("""
                INSERT INTO dicom_study_directory (id, full_path, modality, scan_status)
                VALUES (1, '/path/1', 'CT', 'SCANNED')
                """);

        Map<String, ExecutionContext> result = partitioner.partition(4);

        assertThat(result).hasSize(1);
        ExecutionContext ctx = result.values().iterator().next();
        assertThat(ctx.getLong("minId")).isEqualTo(1L);
        assertThat(ctx.getLong("maxId")).isEqualTo(1L);
    }
}