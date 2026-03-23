package com.planitsquare.medingestex.listener;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectory;
import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectoryDetail;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SkipLoggingListenerTest {

    private final SkipLoggingListener listener = new SkipLoggingListener();

    @Test
    void onSkipInRead_logsWarning() {
        assertThatCode(() -> listener.onSkipInRead(new RuntimeException("read error")))
                .doesNotThrowAnyException();
    }

    @Test
    void onSkipInProcess_logsPathAndError() {
        Path path = Path.of("/test/path/study1");
        assertThatCode(() -> listener.onSkipInProcess(path, new RuntimeException("process error")))
                .doesNotThrowAnyException();
    }

    @Test
    void onSkipInWrite_logsFullPathAndError() {
        DicomStudyDirectoryDetail detail = DicomStudyDirectoryDetail.fromDirName(
                "PT001_20240101_120000_ACC123_CT", "Midterm", "Sub001", "20240101");
        DicomStudyDirectory item = DicomStudyDirectory.of("/test/path/study1", "CT", detail);

        assertThatCode(() -> listener.onSkipInWrite(item, new RuntimeException("write error")))
                .doesNotThrowAnyException();
    }
}