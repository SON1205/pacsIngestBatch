package com.planitsquare.medingestex.pacs.job.directoryScan;

import static org.assertj.core.api.Assertions.assertThat;

import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyDirectoryItemProcessorTest {

    private final StudyDirectoryItemProcessor processor = new StudyDirectoryItemProcessor();

    @Test
    void process_normalPath_extractsAllFields(@TempDir Path tempDir) throws Exception {
        // {rootFolder}/{subFolder}/{date}/{modality}/{studyDir}
        Path studyDir = tempDir.resolve("Midterm/Midterm001/20240101/CT/PT001_20240101_120000_ACC123_CT");
        Files.createDirectories(studyDir);

        DicomStudyDirectory result = processor.process(studyDir);

        assertThat(result.getModality()).isEqualTo("CT");
        assertThat(result.getDetail().getStudyDate()).isEqualTo("20240101");
        assertThat(result.getDetail().getSubFolder()).isEqualTo("Midterm001");
        assertThat(result.getDetail().getRootFolder()).isEqualTo("Midterm");
        assertThat(result.getDetail().getPatientId()).isEqualTo("PT001");
        assertThat(result.getDetail().getExamDate()).isEqualTo("20240101");
        assertThat(result.getDetail().getExamTime()).isEqualTo("120000");
        assertThat(result.getDetail().getAccessionNumber()).isEqualTo("ACC123");
        assertThat(result.getScanStatus()).isEqualTo("SCANNED");
    }

    @Test
    void process_unparsableDirName_pathFieldsExtracted_parsedFieldsNull(@TempDir Path tempDir) throws Exception {
        Path studyDir = tempDir.resolve("Midterm/Midterm001/20240101/CT/shortname");
        Files.createDirectories(studyDir);

        DicomStudyDirectory result = processor.process(studyDir);

        assertThat(result.getModality()).isEqualTo("CT");
        assertThat(result.getDetail().getRootFolder()).isEqualTo("Midterm");
        assertThat(result.getDetail().getSubFolder()).isEqualTo("Midterm001");
        assertThat(result.getDetail().getStudyDate()).isEqualTo("20240101");
        assertThat(result.getDetail().getDirName()).isEqualTo("shortname");
        assertThat(result.getDetail().getPatientId()).isNull();
        assertThat(result.getDetail().getAccessionNumber()).isNull();
    }

    @Test
    void process_fullPathIsAbsolute(@TempDir Path tempDir) throws Exception {
        Path studyDir = tempDir.resolve("Midterm/Midterm001/20240101/CT/PT001_20240101_120000_ACC123_CT");
        Files.createDirectories(studyDir);

        DicomStudyDirectory result = processor.process(studyDir);

        assertThat(result.getFullPath()).startsWith("/");
    }
}
