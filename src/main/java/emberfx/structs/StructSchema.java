package emberfx.structs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed WPILib struct schema, e.g. {@code "Translation2d translation;Rotation2d rotation"}.
 * Supports primitives, nested structs, fixed arrays ({@code double m[4]}), enums
 * ({@code enum {a=1, b=2} int8 mode}) and bit-fields ({@code int8 a:4}). Immutable.
 */
public final class StructSchema {
    /** Field types. {@code size} is in bytes; STRUCT's size comes from the nested schema. */
    public enum Kind {
        BOOL(1, false), CHAR(1, false),
        INT8(1, true), INT16(2, true), INT32(4, true), INT64(8, true),
        UINT8(1, false), UINT16(2, false), UINT32(4, false), UINT64(8, false),
        FLOAT(4, true), DOUBLE(8, true), STRUCT(0, false);

        final int size;
        final boolean signed;

        Kind(int size, boolean signed) {
            this.size = size;
            this.signed = signed;
        }
    }

    /**
     * One declaration.
     *
     * @param structName nested struct name for STRUCT, else null
     * @param arrayLength 0 for a single value, else the fixed array length
     * @param bitWidth 0 for a whole field, else the bit-field width
     * @param enumValues name by value for enum fields, else empty
     */
    public record Field(String name, Kind kind, String structName, int arrayLength, int bitWidth,
                        Map<Long, String> enumValues) {}

    private static final Pattern ENUM = Pattern.compile("^(?:enum)?\\s*\\{([^}]*)\\}\\s*(.*)$", Pattern.DOTALL); // "enum" is optional
    private static final Pattern DECL = Pattern.compile("^(\\w+)\\s+(\\w+)\\s*(?:\\[\\s*(\\d+)\\s*\\]|:\\s*(\\d+))?$");
    private static final int MAX_DEPTH = 16;

    private final String name;
    private final List<Field> fields;

    private StructSchema(String name, List<Field> fields) {
        this.name = name;
        this.fields = List.copyOf(fields);
    }

    public String name() { return name; }

    public List<Field> fields() { return fields; }

    /** Parse schema text. Throws IllegalArgumentException naming the bad declaration. */
    public static StructSchema parse(String name, String text) {
        List<Field> out = new ArrayList<>();
        for (String raw : text.split(";")) {
            String decl = raw.trim();
            if (decl.isEmpty()) continue;
            out.add(parseField(name, decl));
        }
        if (out.isEmpty()) throw new IllegalArgumentException(name + ": empty schema");
        return new StructSchema(name, out);
    }

    private static Field parseField(String struct, String decl) {
        Map<Long, String> enums = Map.of();
        String rest = decl;
        Matcher e = ENUM.matcher(decl);
        if (e.matches()) {
            enums = parseEnum(struct, decl, e.group(1));
            rest = e.group(2).trim();
        }
        Matcher m = DECL.matcher(rest);
        if (!m.matches()) throw new IllegalArgumentException(struct + ": bad declaration \"" + decl + "\"");
        Kind kind = primitive(m.group(1));
        String structName = kind == Kind.STRUCT ? m.group(1) : null;
        int array = m.group(3) == null ? 0 : Integer.parseInt(m.group(3));
        int bits = m.group(4) == null ? 0 : Integer.parseInt(m.group(4));
        if (m.group(3) != null && array <= 0) {
            throw new IllegalArgumentException(struct + ": bad array length in \"" + decl + "\"");
        }
        if (m.group(4) != null) {
            boolean integral = kind != Kind.STRUCT && kind != Kind.FLOAT && kind != Kind.DOUBLE && kind != Kind.CHAR;
            if (!integral || bits <= 0 || bits > kind.size * 8 || (kind == Kind.BOOL && bits != 1)) {
                throw new IllegalArgumentException(struct + ": bad bit-field in \"" + decl + "\"");
            }
        }
        if (!enums.isEmpty() && (kind == Kind.STRUCT || kind == Kind.FLOAT || kind == Kind.DOUBLE)) {
            throw new IllegalArgumentException(struct + ": enum needs an integer type in \"" + decl + "\"");
        }
        return new Field(m.group(2), kind, structName, array, bits, enums);
    }

    private static Map<Long, String> parseEnum(String struct, String decl, String body) {
        Map<Long, String> values = new LinkedHashMap<>();
        for (String item : body.split(",")) {
            if (item.isBlank()) continue;
            String[] kv = item.split("=");
            if (kv.length != 2) throw new IllegalArgumentException(struct + ": bad enum in \"" + decl + "\"");
            try {
                values.put(Long.parseLong(kv[1].trim()), kv[0].trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(struct + ": bad enum value in \"" + decl + "\"", ex);
            }
        }
        if (values.isEmpty()) throw new IllegalArgumentException(struct + ": empty enum in \"" + decl + "\"");
        return Map.copyOf(values);
    }

    private static Kind primitive(String t) {
        return switch (t) {
            case "bool" -> Kind.BOOL;
            case "char" -> Kind.CHAR;
            case "int8" -> Kind.INT8;
            case "int16" -> Kind.INT16;
            case "int32" -> Kind.INT32;
            case "int64" -> Kind.INT64;
            case "uint8" -> Kind.UINT8;
            case "uint16" -> Kind.UINT16;
            case "uint32" -> Kind.UINT32;
            case "uint64" -> Kind.UINT64;
            case "float", "float32" -> Kind.FLOAT;
            case "double", "float64" -> Kind.DOUBLE;
            default -> Kind.STRUCT;
        };
    }

    /** Size in bytes, or -1 if a nested struct can't be resolved (or nesting is cyclic). */
    public int size(Function<String, StructSchema> resolver) {
        return size(resolver, 0);
    }

    private int size(Function<String, StructSchema> resolver, int depth) {
        if (depth > MAX_DEPTH) return -1;
        Cursor c = new Cursor();
        for (Field f : fields) {
            if (f.bitWidth > 0) {
                c.bits(f);
                continue;
            }
            int each = f.kind.size;
            if (f.kind == Kind.STRUCT) {
                StructSchema nested = resolver.apply(f.structName);
                if (nested == null) return -1;
                each = nested.size(resolver, depth + 1);
                if (each < 0) return -1;
            }
            c.plain(each * Math.max(1, f.arrayLength));
        }
        return c.pos;
    }

    /** Tracks byte and bit position while walking fields; shared by size and decode. */
    static final class Cursor {
        int pos;
        int unit;      // offset of the open bit-field storage unit
        int unitBytes; // its size; 0 when no unit is open
        private int shift;

        /**
         * Place a bit-field: returns its shift within the storage unit at {@link #unit}.
         * Per the WPILib struct spec, adjacent bit-fields share a unit when they have the same integer width,
         * and a bool joins whatever integer unit is open if it fits (on its own it takes a uint8).
         */
        int bits(Field f) {
            boolean joins = unitBytes > 0 && shift + f.bitWidth <= unitBytes * 8
                && (f.kind == Kind.BOOL || f.kind.size == unitBytes);
            if (!joins) {
                unitBytes = f.kind.size; // a bool's size is 1, the uint8 the spec gives it
                unit = pos;
                pos += unitBytes;
                shift = 0;
            }
            int at = shift;
            shift += f.bitWidth;
            return at;
        }

        /** Place a whole field of this many bytes: returns its offset. */
        int plain(int bytes) {
            unitBytes = 0;
            int at = pos;
            pos += bytes;
            return at;
        }
    }
}
