// filepath: d:\PythonAutoPublish\GeoServerPublisherJava\src\main\java\com\example\geoserver\App.java
//CLI / 程序入口，仅负责参数解析与调用 Service
package com.example.geoserver;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files; //处理文件路径
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;

import com.example.geoserver.model.BoundingBox;

public class App {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);    // 初始化命令行输入扫描器

        System.out.print("GeoServer URL (default http://localhost:8080/geoserver) : ");
        String geoserverUrl = sc.nextLine().trim();
        if (geoserverUrl.isEmpty()) geoserverUrl = "http://localhost:8080/geoserver";

        System.out.print("Workspace name (e.g., test): ");
        String workspace = sc.nextLine().trim();

        System.out.print("Datastore name (e.g., shapefile): ");
        String datastore = sc.nextLine().trim();

        System.out.print("Folder or .shp path (e.g., F:/数据/156-3857.zip): ");
        String path = sc.nextLine().trim();

        System.out.print("Style name (e.g., auto/point/line/polygon) default auto: ");
        String styleName = sc.nextLine().trim();
        if (styleName.isEmpty()) styleName = "auto";

        // Credentials (simple prompt; or read from env)
        System.out.print("GeoServer username (default admin): ");
        String user = sc.nextLine().trim();
        if (user.isEmpty()) user = "admin";

        System.out.print("GeoServer password (default geoserver): ");
        String pass = sc.nextLine().trim();
        if (pass.isEmpty()) pass = "geoserver";

        // 创建 GeoServerClient 实例，传入连接信息（后续所有操作都通过该实例执行）
        GeoServerClient gs = new GeoServerClient(geoserverUrl, user, pass);

        // Ensure workspace
        if (!gs.workspaceExists(workspace)) {
            boolean ok = gs.createWorkspace(workspace);
            System.out.println(ok ? "Workspace created." : "Failed to create workspace.");
        } else {
            System.out.println("Workspace exists. Skipping.");
        }

        // Resolve folder for datastore
        Path input = Paths.get(path);   //将字符串形式的路径（path参数）转换为Path对象（Java NIO 中的路径表示方式）
        //isRegularFiles()是否是一个regular file(shp)
        if (Files.isRegularFile(input) && input.toString().toLowerCase(Locale.ROOT).endsWith(".shp")) {
            input = input.getParent();  //getParent() acquire parent directory
        }
        if (Files.isRegularFile(input) && input.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            String zipFileName = input.getFileName().toString();
            int dot = zipFileName.lastIndexOf('.');
            if (dot > 0) zipFileName = zipFileName.substring(0, dot);

            ZipShapefilePublisher zp = new ZipShapefilePublisher();
            // 现在传入 Path，并只返回解压目录 Path
            Path extracted = zp.extractZipToDataDir(input, workspace, datastore, zipFileName);
            input = extracted;
        }
        String folder = input.toAbsolutePath().toString();  //将路径转换为绝对路径,字符串形式的路径，无论输入是shapefile or folder

        // Ensure datastore
        if (!gs.datastoreExists(workspace, datastore)) {
            boolean ok = gs.createDirectoryDatastore(workspace, datastore, folder, "GBK");
            System.out.println(ok ? "Datastore created." : "Failed to create datastore.");
        } else {
            System.out.println("Datastore exists. Skipping creation.");
        }

        // Walk *.shp and publish
        int count = 0;
        //Files.newDirectoryStream() create a directorystream，traverse the directory specified by input path
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(input, "*.shp")) {
            for (Path shp : stream) {
                String layer = stripExt(shp.getFileName().toString());  //stripExt():delete the extension name of file
                count++; //record the file number
                publishOne(gs, workspace, datastore, layer, styleName);
            }
        } catch (IOException e) {
            System.err.println("Failed to list shapefiles: " + e.getMessage());
        }
        System.out.println("Done. Processed " + count + " shapefile(s).");
    }

    private static void publishOne(GeoServerClient gs, String ws, String store, String layer, String styleName) throws Exception {
        // Skip if layer already published (workspace:layer)
        if (gs.layerExists(ws, layer)) {
            System.out.printf("Resource named '%s' already exists in namespace: '%s'%n", layer, ws);
            // Recalc BBOX and print preview
            gs.recalcFeatureTypeBBox(ws, store, layer, "nativebbox,latlonbbox");
            Optional<BoundingBox> bbox = gs.getFeatureTypeBBox(ws, store, layer);
            bbox.ifPresent(b -> System.out.println("OpenLayers preview: " + gs.buildOpenLayersPreview(ws, layer, b)));
            return;
        }

        // If resource exists in store, skip publish, just recalc + preview
        if (gs.featureTypeExists(ws, store, layer)) {
            System.out.printf("Layer '%s' already exists in datastore '%s'. Skipping publish.%n", layer, store);
            gs.recalcFeatureTypeBBox(ws, store, layer, "nativebbox,latlonbbox");
            Optional<BoundingBox> bbox = gs.getFeatureTypeBBox(ws, store, layer);
            bbox.ifPresent(b -> System.out.println("OpenLayers preview: " + gs.buildOpenLayersPreview(ws, layer, b)));
            return;
        }

        boolean created = gs.publishFeatureType(ws, store, layer, layer, null); // let .prj define CRS
        if (!created) {
            System.out.printf("Failed to publish layer '%s'.%n", layer);
            return;
        }
        System.out.printf("Layer '%s' published successfully.%n", layer);

        // Recalc and preview
        gs.recalcFeatureTypeBBox(ws, store, layer, "nativebbox,latlonbbox");
        Optional<BoundingBox> bbox = gs.getFeatureTypeBBox(ws, store, layer);
        bbox.ifPresent(b -> System.out.println("OpenLayers preview: " + gs.buildOpenLayersPreview(ws, layer, b)));

        // Optional: apply style if provided
        if (!styleName.isBlank() && !"auto".equalsIgnoreCase(styleName)) {
            boolean styled = gs.setDefaultStyle(ws, layer, styleName);
            System.out.println(styled
                    ? String.format("Style '%s' applied to '%s'.", styleName, layer)
                    : String.format("Failed to apply style '%s' to '%s'.", styleName, layer));
        }
    }

    private static String stripExt(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}