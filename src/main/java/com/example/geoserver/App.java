// filepath: d:\PythonAutoPublish\GeoServerPublisherJava\src\main\java\com\example\geoserver\App.java
package com.example.geoserver;

import com.example.geoserver.model.BoundingBox;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.Optional;
import java.util.Scanner;

public class App {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);

        System.out.print("GeoServer URL (http://localhost:8080/geoserver): ");
        String geoserverUrl = sc.nextLine().trim();
        if (geoserverUrl.isEmpty()) geoserverUrl = "http://localhost:8080/geoserver";

        System.out.print("Workspace name (e.g., test): ");
        String workspace = sc.nextLine().trim();

        System.out.print("Datastore name (e.g., shapefile): ");
        String datastore = sc.nextLine().trim();

        System.out.print("Folder or .shp path (e.g., D:/data/shps): ");
        String path = sc.nextLine().trim();

        System.out.print("Style name (e.g., auto/point/line/polygon). Press Enter to keep empty: ");
        String styleName = sc.nextLine().trim();

        // Credentials (simple prompt; or read from env)
        System.out.print("GeoServer username (default admin): ");
        String user = sc.nextLine().trim();
        if (user.isEmpty()) user = "admin";

        System.out.print("GeoServer password (default geoserver): ");
        String pass = sc.nextLine().trim();
        if (pass.isEmpty()) pass = "geoserver";

        GeoServerClient gs = new GeoServerClient(geoserverUrl, user, pass);

        // Ensure workspace
        if (!gs.workspaceExists(workspace)) {
            boolean ok = gs.createWorkspace(workspace);
            System.out.println(ok ? "Workspace created." : "Failed to create workspace.");
        } else {
            System.out.println("Workspace exists. Skipping.");
        }

        // Resolve folder for datastore
        Path input = Paths.get(path);
        if (Files.isRegularFile(input) && input.toString().toLowerCase(Locale.ROOT).endsWith(".shp")) {
            input = input.getParent();
        }
        String folder = input.toAbsolutePath().toString();

        // Ensure datastore
        if (!gs.datastoreExists(workspace, datastore)) {
            boolean ok = gs.createDirectoryDatastore(workspace, datastore, folder, "GBK");
            System.out.println(ok ? "Datastore created." : "Failed to create datastore.");
        } else {
            System.out.println("Datastore exists. Skipping creation.");
        }

        // Walk *.shp and publish
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(input, "*.shp")) {
            for (Path shp : stream) {
                String layer = stripExt(shp.getFileName().toString());
                count++;
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