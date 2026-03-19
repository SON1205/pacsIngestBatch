package com.planitsquare.medingestex.pacs.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DicomStudyDirectoryDetailTest {

    @Test
    void fromDirName_withFiveOrMoreParts_parsesAllFields() {
        String dirName = "PT001_20240101_120000_ACC123_CT";

        DicomStudyDirectoryDetail detail = DicomStudyDirectoryDetail.fromDirName(
                dirName, "Midterm", "Midterm001", "20240101");

        assertThat(detail.getPatientId()).isEqualTo("PT001");
        assertThat(detail.getExamDate()).isEqualTo("20240101");
        assertThat(detail.getExamTime()).isEqualTo("120000");
        assertThat(detail.getAccessionNumber()).isEqualTo("ACC123");
        assertThat(detail.getRootFolder()).isEqualTo("Midterm");
        assertThat(detail.getSubFolder()).isEqualTo("Midterm001");
        assertThat(detail.getStudyDate()).isEqualTo("20240101");
        assertThat(detail.getDirName()).isEqualTo(dirName);
    }

    @Test
    void fromDirName_withMoreThanFiveParts_parsesFirstFourFields() {
        String dirName = "PT001_20240101_120000_ACC123_CT_extra";

        DicomStudyDirectoryDetail detail = DicomStudyDirectoryDetail.fromDirName(
                dirName, "Midterm", "Midterm001", "20240101");

        assertThat(detail.getPatientId()).isEqualTo("PT001");
        assertThat(detail.getExamDate()).isEqualTo("20240101");
        assertThat(detail.getExamTime()).isEqualTo("120000");
        assertThat(detail.getAccessionNumber()).isEqualTo("ACC123");
    }

    @Test
    void fromDirName_withLessThanFiveParts_parsedFieldsAreNull() {
        String dirName = "PT001_20240101_120000";

        DicomStudyDirectoryDetail detail = DicomStudyDirectoryDetail.fromDirName(
                dirName, "Midterm", "Midterm001", "20240101");

        assertThat(detail.getPatientId()).isNull();
        assertThat(detail.getExamDate()).isNull();
        assertThat(detail.getExamTime()).isNull();
        assertThat(detail.getAccessionNumber()).isNull();
        assertThat(detail.getRootFolder()).isEqualTo("Midterm");
        assertThat(detail.getSubFolder()).isEqualTo("Midterm001");
        assertThat(detail.getStudyDate()).isEqualTo("20240101");
        assertThat(detail.getDirName()).isEqualTo(dirName);
    }

    @Test
    void fromDirName_withEmptyDelimiters_preservesEmptyStrings() {
        String dirName = "____CT";

        DicomStudyDirectoryDetail detail = DicomStudyDirectoryDetail.fromDirName(
                dirName, "Midterm", "Midterm001", "20240101");

        assertThat(detail.getPatientId()).isEmpty();
        assertThat(detail.getExamDate()).isEmpty();
        assertThat(detail.getExamTime()).isEmpty();
        assertThat(detail.getAccessionNumber()).isEmpty();
    }

    @Test
    void fromDirName_withSinglePart_parsedFieldsAreNull() {
        String dirName = "nounderscore";

        DicomStudyDirectoryDetail detail = DicomStudyDirectoryDetail.fromDirName(
                dirName, "Midterm", "Midterm001", "20240101");

        assertThat(detail.getPatientId()).isNull();
        assertThat(detail.getDirName()).isEqualTo(dirName);
    }
}
