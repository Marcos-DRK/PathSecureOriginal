package com.tuempresa.pathsecure;

import java.util.List;

public class Route {
    private String id;
    private String name;
    private String date;
    private List<CustomLatLng> points;

    public Route() {}
    public Route(String id, String name, String date, List<CustomLatLng> points) {
        this.id = id;
        this.name = name;
        this.date = date;
        this.points = points;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDate() { return date; }
    public List<CustomLatLng> getPoints() { return points; }

    public static class CustomLatLng {
        public double latitude;
        public double longitude;

        public CustomLatLng() {}
        public CustomLatLng(double lat, double lng) {
            this.latitude = lat;
            this.longitude = lng;
        }
    }
}