package com.planitsquare.medingestex.pacs.domain;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DicomStudyDirectoryDetail {

    private static final String DELIMITER = "_";
    private static final int MIN_PARTS = 5;

    private String rootFolder;
    private String subFolder;
    private String studyDate;
    private String dirName;
    private String patientId;
    private String examDate;
    private String examTime;
    private String accessionNumber;
 
    /**
     * 폴더명 형식: {patientId}_{examDate}_{examTime}_{accessionNumber}_{modality}
     */
    public static DicomStudyDirectoryDetail fromDirName(String dirName, String rootFolder, String subFolder,
                                                        String studyDate) {
        String[] parts = dirName.split(DELIMITER, -1);

        if (parts.length < MIN_PARTS) {
            return builder()
                    .dirName(dirName).rootFolder(rootFolder)
                    .subFolder(subFolder).studyDate(studyDate)
                    .build();
        }

        return builder()
                .dirName(dirName).rootFolder(rootFolder)
                .subFolder(subFolder).studyDate(studyDate)
                .patientId(parts[0]).examDate(parts[1])
                .examTime(parts[2]).accessionNumber(parts[3])
                .build();
    }
}