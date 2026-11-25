package emberfx.autos;

import java.util.List;

/**
 * What the field tile draws for one auto: ordered path segments of points (meters, WPILib blue-origin
 * coordinates, heading in degrees counter-clockwise), the start pose, and named event markers.
 */
public record AutoPreview(String name, List<List<Pose>> segments, Pose start, List<Marker> markers) {

    /** A field position (meters) and heading (degrees in (-180, 180]). */
    public record Pose(double x, double y, double headingDeg) {
        Pose flipped(FieldSymmetry s, double length, double width) {
            return s == FieldSymmetry.ROTATIONAL
                ? new Pose(length - x, width - y, norm(headingDeg + 180))
                : new Pose(length - x, y, norm(180 - headingDeg));
        }
    }

    /** An event marker (PathPlanner event marker or Choreo event) at a point on the path. */
    public record Marker(String name, Pose point) {}

    /** The same auto seen from the other alliance. Pass the field size in meters. */
    public AutoPreview flipped(FieldSymmetry s, double fieldLength, double fieldWidth) {
        return new AutoPreview(name,
            segments.stream().map(seg -> seg.stream().map(p -> p.flipped(s, fieldLength, fieldWidth)).toList()).toList(),
            start == null ? null : start.flipped(s, fieldLength, fieldWidth),
            markers.stream().map(m -> new Marker(m.name(), m.point().flipped(s, fieldLength, fieldWidth))).toList());
    }

    static double norm(double deg) {
        double d = deg % 360;
        if (d > 180) d -= 360;
        else if (d <= -180) d += 360;
        return d;
    }
}
