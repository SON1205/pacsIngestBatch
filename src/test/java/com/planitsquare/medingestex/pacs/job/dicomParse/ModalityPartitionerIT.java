package com.planitsquare.medingestex.pacs.job.dicomParse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
class ModalityPartitionerIT {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private ModalityPartitioner partitioner;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM dicom_records");
        jdbc.update("DELETE FROM dicom_study_directory_detail");
        jdbc.update("DELETE FROM dicom_study_directory");
        partitioner = new ModalityPartitioner(dataSource);
    }

    @Test
    void partition_emptyTable_returnsEmpty() {
        Map<String, ExecutionContext> result = partitioner.partition(4);
        assertThat(result).isEmpty();
    }

    @Test
    void partition_onlyParsedRows_returnsEmpty() {
        jdbc.update("""
                INSERT INTO dicom_study_directory (id, full_path, modality, scan_status)
                VALUES (1, '/path/1', 'CT', 'PARSED')
                """);

        Map<String, ExecutionContext> result = partitioner.partition(4);
        assertThat(result).isEmpty();
    }

    @Test
    void partition_allPartitionsHaveRequiredKeys() {
        jdbc.update("""
                INSERT INTO dicom_study_directory (id, full_path, modality, scan_status)
                VALUES (1, '/path/1', 'CT', 'SCANNED'),
                       (2, '/path/2', 'MR', 'SCANNED'),
                       (3, '/path/3', 'DX', 'SCANNED'),
                       (4, '/path/4', 'CR', 'SCANNED')
                """);

        Map<String, ExecutionContext> result = partitioner.partition(4);

        assertThat(result).isNotEmpty();
        assertThat(result.values()).allSatisfy(ctx -> {
            assertThat(ctx.getString("modalities")).isNotBlank();
            assertThat(ctx.containsKey("minId")).isTrue();
            assertThat(ctx.containsKey("maxId")).isTrue();
        });
    }

    @Test
    void partition_heavyModalityGetsSubPartitions() {
        // CT 100건 삽입 - gridSize=4 기준 heavy이므로 여러 파티션으로 분할되어야 함
        for (int i = 1; i <= 100; i++) {
            jdbc.update("INSERT INTO dicom_study_directory (id, full_path, modality, scan_status) VALUES (?, ?, 'CT', 'SCANNED')",
                    i, "/ct/path/" + i);
        }
        // DX 5건
        for (int i = 101; i <= 105; i++) {
            jdbc.update("INSERT INTO dicom_study_directory (id, full_path, modality, scan_status) VALUES (?, ?, 'DX', 'SCANNED')",
                    i, "/dx/path/" + i);
        }

        Map<String, ExecutionContext> result = partitioner.partition(4);

        // CT가 여러 파티션에 분산되었는지 확인
        long ctPartitions = result.values().stream()
                .filter(ctx -> ctx.getString("modalities").equals("CT"))
                .count();
        assertThat(ctPartitions).isGreaterThan(1);

        // DX는 light 파티션에 포함
        boolean dxFound = result.values().stream()
                .anyMatch(ctx -> ctx.getString("modalities").contains("DX"));
        assertThat(dxFound).isTrue();
    }

    @Test
    void partition_lightModalitiesGroupedTogether() {
        jdbc.update("""
                INSERT INTO dicom_study_directory (id, full_path, modality, scan_status)
                VALUES (1, '/path/1', 'DX', 'SCANNED'),
                       (2, '/path/2', 'CR', 'SCANNED'),
                       (3, '/path/3', 'US', 'SCANNED'),
                       (4, '/path/4', 'MG', 'SCANNED'),
                       (5, '/path/5', 'NM', 'SCANNED')
                """);

        Map<String, ExecutionContext> result = partitioner.partition(2);

        // 모든 light 모달리티가 포함되어야 함
        Set<String> allModalities = new HashSet<>();
        for (ExecutionContext ctx : result.values()) {
            for (String mod : ctx.getString("modalities").split(",")) {
                allModalities.add(mod);
            }
        }
        assertThat(allModalities).containsExactlyInAnyOrder("DX", "CR", "US", "MG", "NM");
    }

    @Test
    void partition_singleModality_singleRow() {
        jdbc.update("""
                INSERT INTO dicom_study_directory (id, full_path, modality, scan_status)
                VALUES (1, '/path/1', 'DX', 'SCANNED')
                """);

        Map<String, ExecutionContext> result = partitioner.partition(4);

        assertThat(result).hasSize(1);
        ExecutionContext ctx = result.values().iterator().next();
        assertThat(ctx.getString("modalities")).isEqualTo("DX");
    }

    @Test
    void partition_mixedHeavyAndLight_coversAllRows() {
        jdbc.update("""
                INSERT INTO dicom_study_directory (id, full_path, modality, scan_status)
                VALUES (1, '/path/1', 'CT', 'SCANNED'),
                       (2, '/path/2', 'CT', 'SCANNED'),
                       (3, '/path/3', 'MR', 'SCANNED'),
                       (4, '/path/4', 'DX', 'SCANNED'),
                       (5, '/path/5', 'CR', 'SCANNED'),
                       (6, '/path/6', 'CT', 'PARSED')
                """);

        Map<String, ExecutionContext> result = partitioner.partition(4);

        // PARSED 행의 모달리티(CT)도 SCANNED CT가 있으므로 포함되지만, PARSED 행 자체는 Reader에서 필터링됨
        Set<String> allModalities = new HashSet<>();
        for (ExecutionContext ctx : result.values()) {
            for (String mod : ctx.getString("modalities").split(",")) {
                allModalities.add(mod);
            }
        }
        assertThat(allModalities).containsExactlyInAnyOrder("CT", "MR", "DX", "CR");
    }
}