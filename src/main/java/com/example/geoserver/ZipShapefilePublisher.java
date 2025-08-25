package com.example.geoserver;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Utility to extract a ZIP (containing a shapefile) into the GeoServer data folder
 * and return the extracted folder path. This class no longer uploads to GeoServer.
 */
public class ZipShapefilePublisher {
    public String ZipShapefileP(String geoserverUrl, String path, String user, String pass, String workspace, String datastore, String zipFileName) throws Exception {
        File zipFile = new File(path);
        if (!zipFile.exists() || !zipFile.isFile()) {
            throw new IllegalArgumentException("ZIP file not found: " + path);
        }

        // Target folder inside GeoServer data directory (Tomcat webapps path)
        // File shpDir = new File("D:/apache-tomcat-9.0.85/webapps/geoserver/data/data/"+ workspace +"/"+ datastore +"/"+ zipFileName);
        Path shpDir = Paths.get(
            "D:", "apache-tomcat-9.0.85", "webapps", "geoserver", "data", "data",
            workspace, datastore
        );

        // Ensure target directory exists
        if (!Files.exists(shpDir)) Files.createDirectories(shpDir);

        // Unzip into shpDir
        Path zipPath = zipFile.toPath();
        try (InputStream fis = Files.newInputStream(zipPath);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                Path out = shpDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(shpDir)) throw new IllegalStateException("Zip entry outside target dir: " + entry.getName());
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

        String shpDirPath = shpDir.resolve(zipFileName).toString();

        System.out.println("Shapefile extracted to: " + shpDirPath);
        return shpDirPath;
        // System.out.println("Shapefile extracted to: " + shpDirPath.toAbsolutePath());
        // return shpDirPath.toAbsolutePath().toString();
    }
}