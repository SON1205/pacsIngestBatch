package com.planitsquare.medingestex.pacs.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DicomStudyDirectory {

    private Long id;
    private String fullPath;
    private String modality;
    @Builder.Default
    private String scanStatus = "SCANNED";
    
    private DicomStudyDirectoryDetail detail;

    public static DicomStudyDirectory of(String fullPath, String modality, DicomStudyDirectoryDetail detail) {
        return DicomStudyDirectory.builder()
                .fullPath(fullPath)
                .modality(modality)
                .detail(detail)
                .build();
    }
}
