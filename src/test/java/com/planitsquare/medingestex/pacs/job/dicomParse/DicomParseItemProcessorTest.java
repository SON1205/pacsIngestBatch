package com.planitsquare.medingestex.pacs.job.dicomParse;

import static org.assertj.core.api.Assertions.assertThat;

import com.planitsquare.medingestex.pacs.domain.DicomRecord;
import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectoryRow;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DicomParseItemProcessorTest {

    private final DicomParseItemProcessor processor = new DicomParseItemProcessor();

    @Test
    void process_withFullAttributes_buildsDicomRecord(@TempDir Path dir) throws Exception {
        writeDicomFile(dir.resolve("test.dcm"), "PT001", "20240315", "103045.123", "1.2.3.5", "1.2.3.6", "CHEST", "CT",
                "HFS");

        DicomStudyDirectoryRow row = new DicomStudyDirectoryRow(1L, dir.toString(), "CT");

        DicomRecord record = processor.process(row);

        assertThat(record).isNotNull();
        assertThat(record.getDicomStudyDirectoryId()).isEqualTo(1L);
        assertThat(record.getPtNo()).isEqualTo("PT001");
        assertThat(record.getStudyUid()).isEqualTo("1.2.3.5");
        assertThat(record.getSeriesUid()).isEqualTo("1.2.3.6");
        assertThat(record.getBodyPart()).isEqualTo("CHEST");
        assertThat(record.getModality()).isEqualTo("CT");
        assertThat(record.getPatientPosition()).isEqualTo("HFS");
        assertThat(record.getAcquisitionDatetime()).isEqualTo(LocalDateTime.of(2024, 3, 15, 10, 30, 45, 123_000_000));
    }

    @Test
    void process_withNullPatientId_returnsNull(@TempDir Path dir) throws Exception {
        writeDicomFile(dir.resolve("test.dcm"), null, "20240315", "103045.000", "1.2.3.5", "1.2.3.6", null, "CT", null);

        DicomStudyDirectoryRow row = new DicomStudyDirectoryRow(2L, dir.toString(), "CT");

        DicomRecord record = processor.process(row);

        assertThat(record).isNull();
    }

    @Test
    void process_withCentisecondTime_parsesCorrectly(@TempDir Path dir) throws Exception {
        writeDicomFile(dir.resolve("test.dcm"), "PT001", "20240315", "210158.44", "1.2.3.5", "1.2.3.6", null, "CT",
                null);

        DicomStudyDirectoryRow row = new DicomStudyDirectoryRow(4L, dir.toString(), "CT");
        DicomRecord record = processor.process(row);

        assertThat(record).isNotNull();
        assertThat(record.getAcquisitionDatetime()).isEqualTo(LocalDateTime.of(2024, 3, 15, 21, 1, 58, 440_000_000));
    }

    @Test
    void process_withDateOnly_usesStartOfDay(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("test.dcm");
        Attributes fmi = new Attributes();
        fmi.setString(Tag.MediaStorageSOPClassUID, VR.UI, UID.CTImageStorage);
        fmi.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, "1.2.3.4.1");
        fmi.setString(Tag.TransferSyntaxUID, VR.UI, UID.ExplicitVRLittleEndian);

        Attributes dataset = new Attributes();
        dataset.setString(Tag.PatientID, VR.LO, "PT001");
        dataset.setString(Tag.StudyDate, VR.DA, "20240101");
        dataset.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.6");
        dataset.setInt(Tag.InstanceNumber, VR.IS, 1);
        dataset.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.5");
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.1");

        try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(file), UID.ExplicitVRLittleEndian)) {
            dos.writeFileMetaInformation(fmi);
            dataset.writeTo(dos);
        }

        DicomStudyDirectoryRow row = new DicomStudyDirectoryRow(5L, dir.toString(), "CT");
        DicomRecord record = processor.process(row);

        assertThat(record).isNotNull();
        assertThat(record.getAcquisitionDatetime()).isEqualTo(LocalDateTime.of(2024, 1, 1, 0, 0, 0));
    }

    @Test
    void process_withNoDate_throwsException(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("test.dcm");
        Attributes fmi = new Attributes();
        fmi.setString(Tag.MediaStorageSOPClassUID, VR.UI, UID.CTImageStorage);
        fmi.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, "1.2.3.4.1");
        fmi.setString(Tag.TransferSyntaxUID, VR.UI, UID.ExplicitVRLittleEndian);

        Attributes dataset = new Attributes();
        dataset.setString(Tag.PatientID, VR.LO, "PT001");
        dataset.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.6");
        dataset.setInt(Tag.InstanceNumber, VR.IS, 1);
        dataset.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.5");
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.1");

        try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(file), UID.ExplicitVRLittleEndian)) {
            dos.writeFileMetaInformation(fmi);
            dataset.writeTo(dos);
        }

        DicomStudyDirectoryRow row = new DicomStudyDirectoryRow(6L, dir.toString(), "CT");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> processor.process(row))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Neither AcquisitionDate nor StudyDate");
    }

    @Test
    void process_withNoAcquisitionDate_fallsBackToStudyDate(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("test.dcm");
        Attributes fmi = new Attributes();
        fmi.setString(Tag.MediaStorageSOPClassUID, VR.UI, UID.CTImageStorage);
        fmi.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, "1.2.3.4.1");
        fmi.setString(Tag.TransferSyntaxUID, VR.UI, UID.ExplicitVRLittleEndian);

        Attributes dataset = new Attributes();
        dataset.setString(Tag.PatientID, VR.LO, "PT001");
        dataset.setString(Tag.StudyDate, VR.DA, "20240101");
        dataset.setString(Tag.StudyTime, VR.TM, "120000.000");
        dataset.setString(Tag.SeriesInstanceUID, VR.UI, "1.2.3.6");
        dataset.setInt(Tag.InstanceNumber, VR.IS, 1);
        dataset.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.5");
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.1");

        try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(file), UID.ExplicitVRLittleEndian)) {
            dos.writeFileMetaInformation(fmi);
            dataset.writeTo(dos);
        }

        DicomStudyDirectoryRow row = new DicomStudyDirectoryRow(3L, dir.toString(), "CT");

        DicomRecord record = processor.process(row);

        assertThat(record).isNotNull();
        assertThat(record.getAcquisitionDatetime()).isEqualTo(LocalDateTime.of(2024, 1, 1, 12, 0, 0));
    }

    private void writeDicomFile(Path path, String patientId, String acqDate, String acqTime,
                                String studyUid, String seriesUid, String bodyPart,
                                String modality, String patientPosition) throws IOException {
        Attributes fmi = new Attributes();
        fmi.setString(Tag.MediaStorageSOPClassUID, VR.UI, UID.CTImageStorage);
        fmi.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, "1.2.3.4.1");
        fmi.setString(Tag.TransferSyntaxUID, VR.UI, UID.ExplicitVRLittleEndian);

        Attributes dataset = new Attributes();
        if (patientId != null) {
            dataset.setString(Tag.PatientID, VR.LO, patientId);
        }
        if (acqDate != null) {
            dataset.setString(Tag.AcquisitionDate, VR.DA, acqDate);
        }
        if (acqTime != null) {
            dataset.setString(Tag.AcquisitionTime, VR.TM, acqTime);
        }
        if (studyUid != null) {
            dataset.setString(Tag.StudyInstanceUID, VR.UI, studyUid);
        }
        if (seriesUid != null) {
            dataset.setString(Tag.SeriesInstanceUID, VR.UI, seriesUid);
        }
        if (bodyPart != null) {
            dataset.setString(Tag.BodyPartExamined, VR.CS, bodyPart);
        }
        if (modality != null) {
            dataset.setString(Tag.Modality, VR.CS, modality);
        }
        if (patientPosition != null) {
            dataset.setString(Tag.PatientPosition, VR.CS, patientPosition);
        }
        dataset.setInt(Tag.InstanceNumber, VR.IS, 1);
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4.1");

        try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(path), UID.ExplicitVRLittleEndian)) {
            dos.writeFileMetaInformation(fmi);
            dataset.writeTo(dos);
        }
    }
}
