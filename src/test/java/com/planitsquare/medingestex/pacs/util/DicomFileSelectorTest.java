package com.planitsquare.medingestex.pacs.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planitsquare.medingestex.pacs.util.DicomFileSelector.FileSnapshot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DicomFileSelectorTest {

    // ── selectRepresentativeFile (end-to-end) ───────────────────────

    @Test
    void selectRepresentativeFile_ctWithMultipleSeries_selectsLargestSeries(@TempDir Path dir) throws Exception {
        String series1 = "1.2.3.1";
        String series2 = "1.2.3.2";

        writeDicomFile(dir.resolve("s1_1.dcm"), series1, 1, "PT001");
        writeDicomFile(dir.resolve("s1_2.dcm"), series1, 2, "PT001");

        writeDicomFile(dir.resolve("s2_1.dcm"), series2, 1, "PT001");
        writeDicomFile(dir.resolve("s2_2.dcm"), series2, 2, "PT001");
        writeDicomFile(dir.resolve("s2_3.dcm"), series2, 3, "PT001");

        var result = DicomFileSelector.selectRepresentativeFile(dir, "CT");

        assertThat(result.attrs().getString(Tag.SeriesInstanceUID)).isEqualTo(series2);
        assertThat(result.attrs().getInt(Tag.InstanceNumber, 0)).isEqualTo(2);
        assertThat(result.totalSizeBytes()).isGreaterThan(0);
    }

    @Test
    void selectRepresentativeFile_mrSelectsLargestSeries(@TempDir Path dir) throws Exception {
        String series1 = "1.2.3.1";
        writeDicomFile(dir.resolve("s1_1.dcm"), series1, 1, "PT001");
        writeDicomFile(dir.resolve("s1_2.dcm"), series1, 2, "PT001");
        writeDicomFile(dir.resolve("s1_3.dcm"), series1, 3, "PT001");

        var result = DicomFileSelector.selectRepresentativeFile(dir, "MR");

        assertThat(result.attrs().getString(Tag.SeriesInstanceUID)).isEqualTo(series1);
        assertThat(result.attrs().getInt(Tag.InstanceNumber, 0)).isEqualTo(2);
    }

    @Test
    void selectRepresentativeFile_otherModality_selectsMiddleOverall(@TempDir Path dir) throws Exception {
        String series1 = "1.2.3.1";
        String series2 = "1.2.3.2";

        writeDicomFile(dir.resolve("f1.dcm"), series1, 1, "PT001");
        writeDicomFile(dir.resolve("f2.dcm"), series1, 2, "PT001");
        writeDicomFile(dir.resolve("f3.dcm"), series2, 1, "PT001");

        var result = DicomFileSelector.selectRepresentativeFile(dir, "DX");

        assertThat(result.attrs().getString(Tag.SeriesInstanceUID)).isEqualTo(series1);
        assertThat(result.attrs().getInt(Tag.InstanceNumber, 0)).isEqualTo(2);
    }

    @Test
    void selectRepresentativeFile_emptyDirectory_throwsIOException(@TempDir Path dir) {
        assertThatThrownBy(() -> DicomFileSelector.selectRepresentativeFile(dir, "CT"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("No DICOM files");
    }

    @Test
    void selectRepresentativeFile_singleFile_returnsThatFile(@TempDir Path dir) throws Exception {
        writeDicomFile(dir.resolve("only.dcm"), "1.2.3.1", 1, "PT001");

        var result = DicomFileSelector.selectRepresentativeFile(dir, "CT");

        assertThat(result.attrs().getString(Tag.PatientID)).isEqualTo("PT001");
        assertThat(result.attrs().getInt(Tag.InstanceNumber, 0)).isEqualTo(1);
    }

    // ── pickTargetFile (unit, no file I/O) ──────────────────────────

    @Nested
    class PickTargetFileTest {

        @Test
        void ct_selectsMiddleOfLargestSeries() {
            List<FileSnapshot> snapshots = List.of(
                    new FileSnapshot(Path.of("scout_1"), "1.1", 1),
                    new FileSnapshot(Path.of("scout_2"), "1.1", 2),
                    new FileSnapshot(Path.of("axial_1"), "2.1", 1),
                    new FileSnapshot(Path.of("axial_2"), "2.1", 2),
                    new FileSnapshot(Path.of("axial_3"), "2.1", 3),
                    new FileSnapshot(Path.of("axial_4"), "2.1", 4),
                    new FileSnapshot(Path.of("axial_5"), "2.1", 5)
            );

            Path result = DicomFileSelector.pickTargetFile(snapshots, "CT");

            // Largest series = "2.1" (5 files), middle index 2 → instanceNumber 3
            assertThat(result).isEqualTo(Path.of("axial_3"));
        }

        @Test
        void dx_selectsMiddleOfAll() {
            List<FileSnapshot> snapshots = List.of(
                    new FileSnapshot(Path.of("a"), "1.1", 1),
                    new FileSnapshot(Path.of("b"), "1.1", 2),
                    new FileSnapshot(Path.of("c"), "2.1", 1),
                    new FileSnapshot(Path.of("d"), "2.1", 2),
                    new FileSnapshot(Path.of("e"), "2.1", 3)
            );

            Path result = DicomFileSelector.pickTargetFile(snapshots, "DX");

            // Sorted: (1.1,1),(1.1,2),(2.1,1),(2.1,2),(2.1,3) → middle index 2 → (2.1,1)
            assertThat(result).isEqualTo(Path.of("c"));
        }

        @Test
        void singleFile_returnsThatFile() {
            List<FileSnapshot> snapshots = List.of(
                    new FileSnapshot(Path.of("only"), "1.1", 1)
            );

            assertThat(DicomFileSelector.pickTargetFile(snapshots, "CT")).isEqualTo(Path.of("only"));
            assertThat(DicomFileSelector.pickTargetFile(snapshots, "DX")).isEqualTo(Path.of("only"));
        }

        @Test
        void evenCount_selectsUpperMiddle() {
            List<FileSnapshot> snapshots = List.of(
                    new FileSnapshot(Path.of("a"), "1.1", 1),
                    new FileSnapshot(Path.of("b"), "1.1", 2),
                    new FileSnapshot(Path.of("c"), "1.1", 3),
                    new FileSnapshot(Path.of("d"), "1.1", 4)
            );

            Path result = DicomFileSelector.pickTargetFile(snapshots, "CT");

            // size=4, index=4/2=2 → instanceNumber 3
            assertThat(result).isEqualTo(Path.of("c"));
        }

        @Test
        void nullSeriesUid_treatedAsEmptyString() {
            List<FileSnapshot> snapshots = List.of(
                    new FileSnapshot(Path.of("a"), null, 1),
                    new FileSnapshot(Path.of("b"), null, 2),
                    new FileSnapshot(Path.of("c"), "1.1", 1)
            );

            Path result = DicomFileSelector.pickTargetFile(snapshots, "CT");

            // null group (2 files) vs "1.1" group (1 file) → null group is largest, middle = index 1
            assertThat(result).isEqualTo(Path.of("b"));
        }

        @Test
        void equalSizeSeries_stillPicksMiddle() {
            List<FileSnapshot> snapshots = List.of(
                    new FileSnapshot(Path.of("s1_1"), "1.1", 1),
                    new FileSnapshot(Path.of("s1_2"), "1.1", 2),
                    new FileSnapshot(Path.of("s2_1"), "2.1", 1),
                    new FileSnapshot(Path.of("s2_2"), "2.1", 2)
            );

            Path result = DicomFileSelector.pickTargetFile(snapshots, "MR");

            // Both series have 2 files — whichever is picked, result should be middle (index 1)
            assertThat(result.toString()).endsWith("_2");
        }
    }

    // ── isDicomFile ─────────────────────────────────────────────────

    @Test
    void isDicomFile_validDicom_returnsTrue(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("test.dcm");
        writeDicomFile(file, "1.2.3.1", 1, "PT001");

        assertThat(DicomFileSelector.isDicomFile(file)).isTrue();
    }

    @Test
    void isDicomFile_nonDicom_returnsFalse(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("test.txt");
        Files.writeString(file, "not a DICOM file");

        assertThat(DicomFileSelector.isDicomFile(file)).isFalse();
    }

    // ── helper ──────────────────────────────────────────────────────

    private void writeDicomFile(Path path, String seriesUid, int instanceNumber, String patientId) throws IOException {
        Attributes fmi = new Attributes();
        fmi.setString(Tag.MediaStorageSOPClassUID, VR.UI, UID.CTImageStorage);
        fmi.setString(Tag.MediaStorageSOPInstanceUID, VR.UI, "1.2.3.4." + instanceNumber);
        fmi.setString(Tag.TransferSyntaxUID, VR.UI, UID.ExplicitVRLittleEndian);

        Attributes dataset = new Attributes();
        dataset.setString(Tag.PatientID, VR.LO, patientId);
        dataset.setString(Tag.SeriesInstanceUID, VR.UI, seriesUid);
        dataset.setInt(Tag.InstanceNumber, VR.IS, instanceNumber);
        dataset.setString(Tag.StudyInstanceUID, VR.UI, "1.2.3.5");
        dataset.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
        dataset.setString(Tag.SOPInstanceUID, VR.UI, "1.2.3.4." + instanceNumber);

        try (DicomOutputStream dos = new DicomOutputStream(Files.newOutputStream(path), UID.ExplicitVRLittleEndian)) {
            dos.writeFileMetaInformation(fmi);
            dataset.writeTo(dos);
        }
    }
}