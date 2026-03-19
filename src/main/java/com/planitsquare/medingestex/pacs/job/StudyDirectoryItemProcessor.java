package com.planitsquare.medingestex.pacs.job;

import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectory;
import com.planitsquare.medingestex.pacs.domain.DicomStudyDirectoryDetail;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepScope
public class StudyDirectoryItemProcessor implements ItemProcessor<Path, DicomStudyDirectory> {

    @Override
    public DicomStudyDirectory process(Path path) {
        String modality = path.getParent().getFileName().toString();
        String studyDate = path.getParent().getParent().getFileName().toString();
        String subFolder = path.getParent().getParent().getParent().getFileName().toString();
        String rootFolder = path.getParent().getParent().getParent().getParent().getFileName().toString();
        String dirName = path.getFileName().toString();
        DicomStudyDirectoryDetail detail = DicomStudyDirectoryDetail.fromDirName(dirName, rootFolder, subFolder, studyDate);

        return DicomStudyDirectory.of(path.toAbsolutePath().toString(), modality, detail);
    }
}
