package sq.rogue.rosettadrone;

public class Functions {
    public final static double EARTH_RADIUS = 6378137.0;  // Radius of "spherical" earth

    public static double[] metersToLatLng(double northMeters, double eastMeters, double lat) {
        double c = 180 / Math.PI;
        double dLat = northMeters / EARTH_RADIUS * c;
        double dLng = eastMeters / (EARTH_RADIUS * Math.cos(lat / c)) * c;
        return new double[] {dLat, dLng};
    }
}
