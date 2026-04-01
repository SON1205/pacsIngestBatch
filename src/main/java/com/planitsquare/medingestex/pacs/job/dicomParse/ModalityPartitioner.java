package com.planitsquare.medingestex.pacs.job.dicomParse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@RequiredArgsConstructor
public class ModalityPartitioner implements Partitioner {

    private static final Set<String> HEAVY_MODALITIES = Set.of("CT", "MR");
    private static final double HEAVY_WEIGHT = 10.0;
    private static final double LIGHT_WEIGHT = 1.0;

    private final DataSource dataSource;

    record ModalityStats(String modality, long count, long minId, long maxId) {
        double weightedCost() {
            return count * (HEAVY_MODALITIES.contains(modality) ? HEAVY_WEIGHT : LIGHT_WEIGHT);
        }
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        List<ModalityStats> statsList = jdbc.query("""
                SELECT modality, COUNT(*) AS cnt, MIN(id) AS min_id, MAX(id) AS max_id
                FROM dicom_study_directory
                WHERE scan_status = 'SCANNED'
                GROUP BY modality
                """, (rs, rowNum) -> new ModalityStats(
                rs.getString("modality"),
                rs.getLong("cnt"),
                rs.getLong("min_id"),
                rs.getLong("max_id")
        ));

        if (statsList.isEmpty()) {
            return Map.of();
        }

        double totalCost = statsList.stream().mapToDouble(ModalityStats::weightedCost).sum();
        double targetCostPerPartition = totalCost / gridSize;

        Map<String, ExecutionContext> partitions = new HashMap<>();
        int partitionIndex = 0;

        // 1) heavy 모달리티: 서브 파티셔닝
        List<ModalityStats> lightStats = new ArrayList<>();
        for (ModalityStats stats : statsList) {
            if (HEAVY_MODALITIES.contains(stats.modality()) && stats.weightedCost() > targetCostPerPartition) {
                int subCount = (int) Math.ceil(stats.weightedCost() / targetCostPerPartition);
                partitionIndex = createSubPartitions(partitions, partitionIndex, stats, subCount);
            } else {
                lightStats.add(stats);
            }
        }

        // 2) light 모달리티: 탐욕적 빈 패킹
        int remainingSlots = Math.max(gridSize - partitionIndex, 1);
        partitionIndex = packLightModalities(partitions, partitionIndex, lightStats, remainingSlots);

        log.info("Created {} partitions (gridSize hint: {})", partitionIndex, gridSize);
        return partitions;
    }

    private int createSubPartitions(Map<String, ExecutionContext> partitions,
                                    int startIndex, ModalityStats stats, int subCount) {
        long range = stats.maxId() - stats.minId() + 1;
        long subSize = Math.max(range / subCount, 1);

        int index = startIndex;
        long start = stats.minId();
        while (start <= stats.maxId()) {
            long end = Math.min(start + subSize - 1, stats.maxId());
            ExecutionContext ctx = new ExecutionContext();
            ctx.putString("modalities", stats.modality());
            ctx.putLong("minId", start);
            ctx.putLong("maxId", end);
            partitions.put("partition" + index, ctx);
            log.info("Partition {}: modality={}, idRange=[{}-{}]", index, stats.modality(), start, end);
            start = end + 1;
            index++;
        }
        return index;
    }

    private int packLightModalities(Map<String, ExecutionContext> partitions,
                                    int startIndex, List<ModalityStats> lightStats, int slots) {
        if (lightStats.isEmpty()) {
            return startIndex;
        }

        // 비용 내림차순 정렬
        lightStats.sort(Comparator.comparingDouble(ModalityStats::weightedCost).reversed());

        // 빈 초기화
        List<List<String>> bins = new ArrayList<>();
        double[] binCosts = new double[slots];
        for (int i = 0; i < slots; i++) {
            bins.add(new ArrayList<>());
        }

        // 탐욕적 배정: 가장 비용 낮은 빈에 넣기
        for (ModalityStats stats : lightStats) {
            int minBin = 0;
            for (int i = 1; i < slots; i++) {
                if (binCosts[i] < binCosts[minBin]) {
                    minBin = i;
                }
            }
            bins.get(minBin).add(stats.modality());
            binCosts[minBin] += stats.weightedCost();
        }

        int index = startIndex;
        for (List<String> bin : bins) {
            if (bin.isEmpty()) {
                continue;
            }
            ExecutionContext ctx = new ExecutionContext();
            ctx.putString("modalities", String.join(",", bin));
            ctx.putLong("minId", 0L);
            ctx.putLong("maxId", Long.MAX_VALUE);
            partitions.put("partition" + index, ctx);
            log.info("Partition {}: modalities={}, idRange=[all]", index, bin);
            index++;
        }
        return index;
    }
}