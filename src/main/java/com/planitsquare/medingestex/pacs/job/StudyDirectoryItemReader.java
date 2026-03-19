package com.planitsquare.medingestex.pacs.job;

import com.planitsquare.medingestex.config.PacsProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.ItemStreamException;
import org.springframework.batch.infrastructure.item.ItemStreamReader;
import org.springframework.stereotype.Component;

/**
 * depth별 Files.list + sorted 방식으로 디렉토리를 순회한다.
 * <p>
 * 정렬된 순서로 탐색하므로 재시작 시 skip(N)이 정확하고, sub-folder 레벨에서 패턴 필터링하여 불필요한 하위 탐색을 방지한다.
 * <p>
 * 구조: {rootFolder}/{subFolder}/{date}/{modality}/{studyDir}
 */
@Slf4j
@Component
@StepScope
@RequiredArgsConstructor
public class StudyDirectoryItemReader implements ItemStreamReader<Path> {

    private static final String CURRENT_INDEX_KEY = "study.directory.reader.current.index";

    private final PacsProperties pacsProperties;
    private Iterator<Path> iterator;
    private int currentIndex;

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        List<Path> studyDirs = collectAllStudyDirs();
        log.info("Total study directories found: {}", studyDirs.size());

        int skipCount = executionContext.containsKey(CURRENT_INDEX_KEY)
                ? executionContext.getInt(CURRENT_INDEX_KEY)
                : 0;
        currentIndex = skipCount;

        if (skipCount > 0) {
            log.info("Resuming from index {}", skipCount);
        }

        iterator = studyDirs.listIterator(skipCount);
    }

    @Override
    public Path read() {
        if (iterator == null || !iterator.hasNext()) {
            return null;
        }
        currentIndex++;
        return iterator.next();
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putInt(CURRENT_INDEX_KEY, currentIndex);
    }

    @Override
    public void close() throws ItemStreamException {
        iterator = null;
    }

    private List<Path> collectAllStudyDirs() {
        Path rootPath = Path.of(pacsProperties.getRootPath());
        Pattern subFolderRegex = compileSubFolderPattern();

        List<Path> studyDirs = new ArrayList<>();
        for (String rootFolder : pacsProperties.getRootFolders()) {
            Path rootFolderPath = rootPath.resolve(rootFolder);
            if (!Files.isDirectory(rootFolderPath)) {
                log.warn("Root folder does not exist: {}", rootFolderPath);
                continue;
            }
            listSubFolders(rootFolderPath, subFolderRegex)
                    .forEach(subFolder -> studyDirs.addAll(collectStudyDirsUnder(subFolder)));
        }
        return studyDirs;
    }

    private Pattern compileSubFolderPattern() {
        String pattern = pacsProperties.getSubFolderPattern();
        if (pattern != null && !pattern.isBlank()) {
            log.info("Sub-folder filter pattern: '{}'", pattern);
            return Pattern.compile(pattern);
        }
        return Pattern.compile(".*");
    }

    /**
     * subFolder 목록을 반환한다. 패턴이 설정된 경우 매칭되는 폴더만 반환.
     */
    private List<Path> listSubFolders(Path rootFolderPath, Pattern subFolderRegex) {
        return listSorted(rootFolderPath).stream()
                .filter(path -> subFolderRegex.matcher(path.getFileName().toString()).matches())
                .toList();
    }

    private List<Path> listSorted(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            log.warn("Failed to list directory: {}", dir, e);
            return List.of();
        }
    }

    private List<Path> collectStudyDirsUnder(Path subFolder) {
        return listSorted(subFolder).stream()
                .flatMap(date -> listSorted(date).stream())
                .flatMap(modality -> listSorted(modality).stream())
                .toList();
    }
}