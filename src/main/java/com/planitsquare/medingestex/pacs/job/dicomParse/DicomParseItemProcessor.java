package com.planitsquare.medingestex.pacs.job.dicomParse;

import com.planitsquare.medingestex.pacs.domain.DicomRecord;
import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectoryRow;
import com.planitsquare.medingestex.pacs.util.DicomFileSelector;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import lombok.extern.slf4j.Slf4j;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.springframework.batch.infrastructure.item.ItemProcessor;

@Slf4j
public class DicomParseItemProcessor implements ItemProcessor<DicomStudyDirectoryRow, DicomRecord> {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter[] TIME_FMTS = {
            DateTimeFormatter.ofPattern("HHmmss.SSSSSS"),
            DateTimeFormatter.ofPattern("HHmmss.SSSSS"),
            DateTimeFormatter.ofPattern("HHmmss.SSSS"),
            DateTimeFormatter.ofPattern("HHmmss.SSS"),
            DateTimeFormatter.ofPattern("HHmmss.SS"),
            DateTimeFormatter.ofPattern("HHmmss.S"),
            DateTimeFormatter.ofPattern("HHmmss"),
            DateTimeFormatter.ofPattern("HHmm"),
    };

    @Override
    public DicomRecord process(DicomStudyDirectoryRow row) throws Exception {
        Path studyDir = Path.of(row.fullPath());

        long start = System.nanoTime();
        Attributes attrs = DicomFileSelector.selectRepresentativeFile(studyDir, row.modality());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        logSlowSelect(elapsedMs, row);

        String patientId = attrs.getString(Tag.PatientID);
        if (patientId == null) {
            log.warn("PatientID is null, filtering out: {}", row.fullPath());
            return null;
        }

        LocalDateTime acquisitionDatetime = parseAcquisitionDatetime(attrs);
        DicomRecord record = buildRecord(row, attrs, patientId, acquisitionDatetime);
        logParsedResult(record, elapsedMs);
        return record;
    }

    private DicomRecord buildRecord(DicomStudyDirectoryRow row, Attributes attrs,
                                    String patientId, LocalDateTime acquisitionDatetime) {
        return DicomRecord.builder()
                .dicomStudyDirectoryId(row.id())
                .ptNo(patientId)
                .filePath(row.fullPath())
                .acquisitionDatetime(acquisitionDatetime)
                .studyUid(attrs.getString(Tag.StudyInstanceUID))
                .seriesUid(attrs.getString(Tag.SeriesInstanceUID))
                .bodyPart(attrs.getString(Tag.BodyPartExamined))
                .modality(attrs.getString(Tag.Modality))
                .patientPosition(attrs.getString(Tag.PatientPosition))
                .build();
    }

    private void logSlowSelect(long elapsedMs, DicomStudyDirectoryRow row) {
        if (elapsedMs > 3000) {
            log.warn("Slow DICOM select: {}ms, path: {}", elapsedMs, row.fullPath());
        }
    }

    private void logParsedResult(DicomRecord record, long elapsedMs) {
        log.debug("Parsed [id={}]: ptNo={}, modality={}, bodyPart={}, acqDt={}, elapsed={}ms",
                record.getDicomStudyDirectoryId(), record.getPtNo(), record.getModality(),
                record.getBodyPart(), record.getAcquisitionDatetime(), elapsedMs);
    }

    private LocalDateTime parseAcquisitionDatetime(Attributes attrs) {
        LocalDate date = parseDate(attrs, Tag.AcquisitionDate);
        if (date == null) {
            date = parseDate(attrs, Tag.StudyDate);
        }
        if (date == null) {
            throw new IllegalStateException("Neither AcquisitionDate nor StudyDate found");
        }

        LocalTime time = parseTime(attrs, Tag.AcquisitionTime);
        if (time == null) {
            time = parseTime(attrs, Tag.StudyTime);
        }

        return time != null ? LocalDateTime.of(date, time) : date.atStartOfDay();
    }

    private LocalDate parseDate(Attributes attrs, int tag) {
        String value = attrs.getString(tag);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FMT);
        } catch (DateTimeParseException e) {
            log.warn("Failed to parse date '{}' for tag {}", value, Integer.toHexString(tag));
            return null;
        }
    }

    private LocalTime parseTime(Attributes attrs, int tag) {
        String value = attrs.getString(tag);
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter fmt : TIME_FMTS) {
            try {
                return LocalTime.parse(trimmed, fmt);
            } catch (DateTimeParseException ignored) {
            }
        }
        log.warn("Failed to parse time '{}' for tag {}", value, Integer.toHexString(tag));
        return null;
    }
}
