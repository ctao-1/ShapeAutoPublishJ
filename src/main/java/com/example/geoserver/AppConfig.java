//配置（从 env / properties 读取 GEOSERVER_DATA_DIR 等）
package com.example.geoserver;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 应用配置（简单、可集中修改）。后续可扩展为从环境变量或命令行读取。
 */
public final class AppConfig {
    private AppConfig() {}

    // Tomcat 下 GeoServer data/data 根目录（推荐改为可配置）
    public static final Path GEOSERVER_DATA_DIR = Paths.get(
            "D:", "apache-tomcat-9.0.85", "webapps", "geoserver", "data", "data"
    );
}