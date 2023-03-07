package sq.rogue.rosettadrone;

public class Functions {
    public final static double EARTH_RADIUS = 6378137.0;  // Radius of "spherical" earth

    public static double[] metersToLatLng(double northMeters, double eastMeters, double lat) {
        double c = 180 / Math.PI;
        double dLat = northMeters / EARTH_RADIUS * c;
        double dLng = eastMeters / (EARTH_RADIUS * Math.cos(lat / c)) * c;
        return new double[] {dLat, dLng};
    }

    /**
     * Returns North and East increments if we fly forwardMeters and rightMeters while we are heading to angle 'yaw'.
     * See also: getLatLngDiff()
     *
     * @param yaw   Current yaw (heading) in radians. If we are heading north (NED), a yaw angle of 90° is considered pointing to the east
     * @param forwardMeters Meters to add in forward direction
     * @param rightMeters   Meters to add to right direction
     * @return (dNorth, dEast)
     */
    public static double[] getNorthEastDiff(double yaw, double forwardMeters, double rightMeters) {
        double dNorth = forwardMeters * Math.cos(yaw) - rightMeters * Math.sin(yaw);
        double dEast = forwardMeters * Math.sin(yaw) + rightMeters * Math.cos(yaw);
        return new double[] {dNorth, dEast};
    }

    /**
     * Returns Latitude and Longitude increments if we fly forwardMeters and rightMeters while we are heading to angle 'yaw'.
     *
     * @param lat   Current Latitude (Longitude is not required).
     * @param yaw   Current yaw (heading) in radians. If we are heading north (NED), a yaw angle of 90° is considered pointing to the east
     * @param forwardMeters Meters to add in forward direction
     * @param rightMeters   Meters to add to right direction
     * @return (dLat, dLng)
     */
    public static double[] getLatLngDiff(double lat, double yaw, double forwardMeters, double rightMeters) {
        double dMeters[] = getNorthEastDiff(yaw, forwardMeters, rightMeters);
        return Functions.metersToLatLng(dMeters[0], dMeters[1], lat);
    }
}
