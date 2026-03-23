package com.planitsquare.medingestex.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "pacs")
public class PacsProperties {

    private String rootPath;
    private List<String> rootFolders;
    private String subFolderPattern;
    private DirectoryScan directoryScan = new DirectoryScan();
    private DicomParse dicomParse = new DicomParse();

    @Getter
    @Setter
    public static class DirectoryScan {
        private int chunkSize = 100;
        private int skipLimit = 100;
        private int retryLimit = 3;
    }

    @Getter
    @Setter
    public static class DicomParse {
        private int chunkSize = 10;
        private int skipLimit = 1000;
        private int gridSize = 8;
        private int threadCount = 8;
    }
}