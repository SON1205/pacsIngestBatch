package com.planitsquare.medingestex.pacs.job.dicomParse;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class IdRangePartitioner implements Partitioner {

    private final DataSource dataSource;

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Long minId = jdbc.queryForObject(
                "SELECT COALESCE(MIN(id), 0) FROM dicom_study_directory WHERE scan_status = 'SCANNED'", Long.class);
        Long maxId = jdbc.queryForObject(
                "SELECT COALESCE(MAX(id), 0) FROM dicom_study_directory WHERE scan_status = 'SCANNED'", Long.class);

        Map<String, ExecutionContext> partitions = new HashMap<>();
        if (minId == null || maxId == null || minId == 0 || maxId == 0) {
            return partitions;
        }

        long range = maxId - minId + 1;
        long partitionSize = Math.max(range / gridSize, 1);

        int partitionIndex = 0;
        long start = minId;
        while (start <= maxId) {
            long end = Math.min(start + partitionSize - 1, maxId);
            ExecutionContext ctx = new ExecutionContext();
            ctx.putLong("minId", start);
            ctx.putLong("maxId", end);
            partitions.put("partition" + partitionIndex, ctx);
            start = end + 1;
            partitionIndex++;
        }

        return partitions;
    }
}
