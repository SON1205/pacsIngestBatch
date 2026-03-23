package com.planitsquare.medingestex.pacs.util;

import static lombok.AccessLevel.PRIVATE;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NoArgsConstructor;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.io.DicomInputStream;

/**
 * 2-pass approach:
 * <p>
 * 1st pass — scan all files, collect lightweight metadata (Path + SeriesUID + InstanceNumber)
 * <p>
 * 2nd pass — read full header of the single selected representative file
 */
@NoArgsConstructor(access = PRIVATE)
public class DicomFileSelector {
    private static final byte[] DICM_MAGIC = {'D', 'I', 'C', 'M'};

    private static final int DICM_OFFSET = 128;
    private static final int SNAPSHOT_STOP_TAG = Tag.InstanceNumber + 1;
    private static final Set<String> MULTI_SERIES_MODALITIES = Set.of("CT", "MR");

    public static Attributes selectRepresentativeFile(Path studyDir, String modality) throws IOException {
        List<FileSnapshot> snapshots = collectFileSnapshots(studyDir);
        if (snapshots.isEmpty()) {
            throw new IOException("No DICOM files found in: " + studyDir);
        }

        Path targetFile = pickTargetFile(snapshots, modality);
        return readDicomHeader(targetFile);
    }

    private static List<FileSnapshot> collectFileSnapshots(Path dir) throws IOException {
        List<FileSnapshot> snapshots = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, Files::isRegularFile)) {
            for (Path path : stream) {
                try {
                    snapshots.add(readFileSnapshot(path));
                } catch (IOException ignored) {
                    // skip non-DICOM or unreadable files
                }
            }
        }
        return snapshots;
    }

    private static FileSnapshot readFileSnapshot(Path file) throws IOException {
        try (DicomInputStream dis = new DicomInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
            return FileSnapshot.from(file, dis.readDataset(SNAPSHOT_STOP_TAG));
        }
    }

    static Path pickTargetFile(List<FileSnapshot> snapshots, String modality) {
        if (MULTI_SERIES_MODALITIES.contains(modality)) {
            return pickFromLargestSeries(snapshots);
        }
        return pickFromAll(snapshots);
    }

    // 동일 크기의 시리즈가 여러 개면 비결정적 선택 (핵심 태그는 시리즈 무관하게 동일하므로 무방)
    private static Path pickFromLargestSeries(List<FileSnapshot> snapshots) {
        Map<String, List<FileSnapshot>> bySeries = snapshots.stream()
                .collect(Collectors.groupingBy(s -> s.seriesUid != null ? s.seriesUid : ""));

        List<FileSnapshot> largestSeries = bySeries.values().stream()
                .max(Comparator.comparingInt(List::size))
                .orElseThrow();

        return pickMiddleFile(largestSeries);
    }

    private static Path pickFromAll(List<FileSnapshot> snapshots) {
        List<FileSnapshot> sorted = snapshots.stream()
                .sorted(Comparator
                        .comparing((FileSnapshot s) -> s.seriesUid != null ? s.seriesUid : "")
                        .thenComparingInt(s -> s.instanceNumber))
                .toList();

        return sorted.get(sorted.size() / 2).path;
    }

    private static Path pickMiddleFile(List<FileSnapshot> snapshots) {
        List<FileSnapshot> sorted = snapshots.stream()
                .sorted(Comparator.comparingInt(s -> s.instanceNumber))
                .toList();
        return sorted.get(sorted.size() / 2).path;
    }

    public static Attributes readDicomHeader(Path file) throws IOException {
        try (DicomInputStream dis = new DicomInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
            return dis.readDataset();
        }
    }

    record FileSnapshot(Path path, String seriesUid, int instanceNumber) {
        static FileSnapshot from(Path path, Attributes attrs) {
            return new FileSnapshot(
                    path,
                    attrs.getString(Tag.SeriesInstanceUID),
                    attrs.getInt(Tag.InstanceNumber, 0)
            );
        }
    }

    static boolean isDicomFile(Path file) throws IOException {
        try (InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
            long skipped = is.skip(DICM_OFFSET);
            if (skipped < DICM_OFFSET) {
                return false;
            }
            byte[] magic = new byte[4];
            int read = is.read(magic);
            return read == 4 && Arrays.equals(magic, DICM_MAGIC);
        }
    }
}