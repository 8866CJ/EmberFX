package emberfx.structs;

import java.util.List;

/**
 * A decoded struct: ordered field names and values. Values are Double (floats), Long (integers,
 * uint64 as raw bits), Boolean, String (char arrays, enum names), StructValue, or an immutable
 * List of those for fixed arrays.
 */
public record StructValue(String type, List<String> names, List<Object> values) {
    public StructValue {
        names = List.copyOf(names);
        values = List.copyOf(values);
    }

    /** The named field, or null. */
    public Object get(String field) {
        int i = names.indexOf(field);
        return i < 0 ? null : values.get(i);
    }

    /** A numeric field as double, or NaN if missing or not a number. */
    public double number(String field) {
        return get(field) instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    /** A nested struct field, or null. */
    public StructValue struct(String field) {
        return get(field) instanceof StructValue s ? s : null;
    }

    /** An array field, or null. */
    public List<?> list(String field) {
        return get(field) instanceof List<?> l ? l : null;
    }
}
