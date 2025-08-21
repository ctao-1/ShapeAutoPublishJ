// filepath: d:\PythonAutoPublish\GeoServerPublisherJava\src\main\java\com\example\geoserver\model\BoundingBox.java
package com.example.geoserver.model;

public class BoundingBox {
    public double minx;
    public double miny;
    public double maxx;
    public double maxy;

    public BoundingBox() {}

    public BoundingBox(double minx, double miny, double maxx, double maxy) {
        this.minx = minx;
        this.miny = miny;
        this.maxx = maxx;
        this.maxy = maxy;
    }

    @Override
    public String toString() {
        return minx + "," + miny + "," + maxx + "," + maxy;
    }
}