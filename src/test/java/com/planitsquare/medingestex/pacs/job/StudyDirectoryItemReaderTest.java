package com.planitsquare.medingestex.pacs.job;

import com.planitsquare.medingestex.config.PacsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudyDirectoryItemReaderTest {

    @TempDir
    Path tempDir;

    private PacsProperties pacsProperties;

    @BeforeEach
    void setUp() {
        pacsProperties = new PacsProperties();
        pacsProperties.setRootPath(tempDir.toString());
        pacsProperties.setRootFolders(List.of("Midterm"));
        pacsProperties.setSubFolderPattern("Midterm\\d{3}");
    }

    @Test
    void read_normalStructure_returnsAllStudyDirs() throws Exception {
        createStudyDir("Midterm/Midterm001/20240101/CT/study1");
        createStudyDir("Midterm/Midterm001/20240101/CT/study2");
        createStudyDir("Midterm/Midterm001/20240102/MR/study3");

        StudyDirectoryItemReader reader = new StudyDirectoryItemReader(pacsProperties);
        reader.open(new ExecutionContext());

        List<Path> results = readAll(reader);
        reader.close();

        assertThat(results).hasSize(3);
    }

    @Test
    void read_subFolderPatternFiltering_returnsOnlyMatching() throws Exception {
        createStudyDir("Midterm/Midterm001/20240101/CT/study1");
        createStudyDir("Midterm/OtherFolder/20240101/CT/study2");

        StudyDirectoryItemReader reader = new StudyDirectoryItemReader(pacsProperties);
        reader.open(new ExecutionContext());

        List<Path> results = readAll(reader);
        reader.close();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getFileName().toString()).isEqualTo("study1");
    }

    @Test
    void read_sortedAlphabetically() throws Exception {
        createStudyDir("Midterm/Midterm001/20240101/CT/zzz_study");
        createStudyDir("Midterm/Midterm001/20240101/CT/aaa_study");

        StudyDirectoryItemReader reader = new StudyDirectoryItemReader(pacsProperties);
        reader.open(new ExecutionContext());

        List<Path> results = readAll(reader);
        reader.close();

        assertThat(results.get(0).getFileName().toString()).isEqualTo("aaa_study");
        assertThat(results.get(1).getFileName().toString()).isEqualTo("zzz_study");
    }

    @Test
    void read_emptyDirectory_returnsNull() throws Exception {
        Files.createDirectories(tempDir.resolve("Midterm"));

        StudyDirectoryItemReader reader = new StudyDirectoryItemReader(pacsProperties);
        reader.open(new ExecutionContext());

        assertThat(reader.read()).isNull();
        reader.close();
    }

    @Test
    void read_nonExistentRootFolder_returnsNull() throws Exception {
        pacsProperties.setRootFolders(List.of("NonExistent"));

        StudyDirectoryItemReader reader = new StudyDirectoryItemReader(pacsProperties);
        reader.open(new ExecutionContext());

        assertThat(reader.read()).isNull();
        reader.close();
    }

    @Test
    void restart_skipsAlreadyProcessedItems() throws Exception {
        createStudyDir("Midterm/Midterm001/20240101/CT/study1");
        createStudyDir("Midterm/Midterm001/20240101/CT/study2");
        createStudyDir("Midterm/Midterm001/20240101/CT/study3");

        ExecutionContext ctx = new ExecutionContext();
        ctx.putInt("study.directory.reader.current.index", 2);

        StudyDirectoryItemReader reader = new StudyDirectoryItemReader(pacsProperties);
        reader.open(ctx);

        List<Path> results = readAll(reader);
        reader.close();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getFileName().toString()).isEqualTo("study3");
    }

    @Test
    void update_savesCurrentIndex() throws Exception {
        createStudyDir("Midterm/Midterm001/20240101/CT/study1");
        createStudyDir("Midterm/Midterm001/20240101/CT/study2");

        StudyDirectoryItemReader reader = new StudyDirectoryItemReader(pacsProperties);
        ExecutionContext ctx = new ExecutionContext();
        reader.open(ctx);
        reader.read();

        reader.update(ctx);

        assertThat(ctx.getInt("study.directory.reader.current.index")).isEqualTo(1);
    }

    @Test
    void close_subsequentReadReturnsNull() throws Exception {
        createStudyDir("Midterm/Midterm001/20240101/CT/study1");

        StudyDirectoryItemReader reader = new StudyDirectoryItemReader(pacsProperties);
        reader.open(new ExecutionContext());
        reader.close();

        assertThat(reader.read()).isNull();
    }

    @Test
    void read_noSubFolderPattern_returnsAllSubFolders() throws Exception {
        pacsProperties.setSubFolderPattern(null);
        createStudyDir("Midterm/Midterm001/20240101/CT/study1");
        createStudyDir("Midterm/OtherFolder/20240101/CT/study2");

        StudyDirectoryItemReader reader = new StudyDirectoryItemReader(pacsProperties);
        reader.open(new ExecutionContext());

        List<Path> results = readAll(reader);
        reader.close();

        assertThat(results).hasSize(2);
    }

    private void createStudyDir(String relativePath) throws Exception {
        Files.createDirectories(tempDir.resolve(relativePath));
    }

    private List<Path> readAll(StudyDirectoryItemReader reader) throws Exception {
        List<Path> results = new ArrayList<>();
        Path item;
        while ((item = reader.read()) != null) {
            results.add(item);
        }
        return results;
    }
}
