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
    private int chunkSize = 100;
    private int skipLimit = 100;
}
