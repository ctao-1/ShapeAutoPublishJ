//只负责 unzip -> 返回解压目录（不做上传/REST）
package com.example.geoserver;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 只负责把传入的 zip 解到 GeoServer data 下 workspace/datastore/{zipBaseName}
 * 并返回解压后的目录 Path。
 */
public class ZipShapefilePublisher {

    /**
     * 解压 zip 到 {GEOSERVER_DATA_DIR}/{workspace}/{datastore}/{zipBaseName} 并返回该目录。
     * zipFilePath: 原 zip 的文件路径
     * zipBaseName: 不带扩展名的 zip 名，例如 "156-3857"
     */
    public Path extractZipToDataDir(Path zipFilePath, String workspace, String datastore, String zipBaseName) throws Exception {
        Path targetRoot = AppConfig.GEOSERVER_DATA_DIR.resolve(Paths.get(workspace, datastore));    // 解压目标根目录
        // 使用通用工具解压
        return (FileUtils.extractZipTo(zipFilePath, targetRoot)).resolve(zipBaseName);  // 解压后的目录
    }
}