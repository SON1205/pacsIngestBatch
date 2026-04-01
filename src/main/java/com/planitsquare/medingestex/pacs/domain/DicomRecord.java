package com.planitsquare.medingestex.pacs.domain;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DicomRecord {
    private Long dicomStudyDirectoryId;
    private String ptNo;
    private String filePath;
    private LocalDateTime acquisitionDatetime;
    private String studyUid;
    private String seriesUid;
    private String bodyPart;
    private String modality;
    private String patientPosition;
    private long totalSizeBytes;
}
