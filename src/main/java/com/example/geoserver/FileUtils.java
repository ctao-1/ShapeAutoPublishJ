//通用文件工具（安全解压 zip 等）
package com.example.geoserver;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 一些与文件/ZIP 相关的通用方法。
 */
public final class FileUtils {
    private FileUtils() {}

    /**
     * 将 zipPath 解压到 targetDir（会创建 targetDir），返回 targetDir。
     * 防止 zip-slip：任何 entry 解出的路径都必须以 targetDir 开头。
     */
    public static Path extractZipTo(Path zipPath, Path targetDir) throws Exception {
        if (!Files.exists(zipPath) || !Files.isRegularFile(zipPath)) {
            throw new IllegalArgumentException("ZIP not found: " + zipPath);
        }
        Files.createDirectories(targetDir);

        try (InputStream fis = Files.newInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                Path out = targetDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(targetDir)) {
                    throw new IllegalStateException("Zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) os.write(buffer, 0, len);
                    }
                }
            }
        }
        return targetDir;
    }
}