package emberfx.structs;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StructTest {
    private StructRegistry reg;

    @BeforeEach
    void setUp() {
        reg = new StructRegistry();
        reg.addSchema("Rotation2d", "double value");
        reg.addSchema("Translation2d", "double x;double y");
        reg.addSchema("Pose2d", "Translation2d translation;Rotation2d rotation");
        reg.addSchema("SwerveModuleState", "double speed;Rotation2d angle");
        reg.addSchema("ChassisSpeeds", "double vx;double vy;double omega");
    }

    private static ByteBuffer buf(int n) {
        return ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    void parsesAllForms() {
        StructSchema s = StructSchema.parse("T", "bool a;char b;int8 c;uint16 d;float e;float64 f;double m[4];"
            + "enum {x=1, y=2} int8 mode;int8 lo:4;int8 hi:4;Pose2d p;");
        assertEquals(11, s.fields().size());
        assertEquals(StructSchema.Kind.DOUBLE, s.fields().get(5).kind());
        assertEquals(4, s.fields().get(6).arrayLength());
        assertEquals("y", s.fields().get(7).enumValues().get(2L));
        assertEquals(4, s.fields().get(8).bitWidth());
        assertEquals("Pose2d", s.fields().get(10).structName());
    }

    @Test
    void rejectsBadSchemas() {
        assertThrows(IllegalArgumentException.class, () -> StructSchema.parse("T", "double"));
        assertThrows(IllegalArgumentException.class, () -> StructSchema.parse("T", "double x:4"));
        assertThrows(IllegalArgumentException.class, () -> StructSchema.parse("T", "int8 x:9"));
        assertThrows(IllegalArgumentException.class, () -> StructSchema.parse("T", ""));
        assertFalse(reg.addSchema("Bad", "nonsense"));
        assertNull(reg.schema("Bad"));
    }

    @Test
    void sizes() {
        assertEquals(24, reg.size("Pose2d"));
        assertEquals(-1, reg.size("Nope"));
        reg.addSchema("Holder", "Missing m");
        assertEquals(-1, reg.size("Holder"));
        reg.addSchema("Missing", "int16 v");
        assertEquals(2, reg.size("Holder"));
    }

    @Test
    void decodesPose2d() {
        byte[] raw = buf(24).putDouble(1.2).putDouble(3.4).putDouble(Math.PI / 2).array();
        StructValue v = reg.decode("Pose2d", raw);
        double[] p = Poses.pose2d(v);
        assertEquals(1.2, p[0], 1e-12);
        assertEquals(3.4, p[1], 1e-12);
        assertEquals(90.0, p[2], 1e-9);
        assertEquals("Pose2d(x=1.20, y=3.40, rot=90.0 deg)", StructFormat.toText(v));
    }

    @Test
    void decodesPose2dArray() {
        byte[] raw = buf(48).putDouble(1).putDouble(2).putDouble(0).putDouble(3).putDouble(4).putDouble(Math.PI).array();
        @SuppressWarnings("unchecked")
        List<StructValue> a = (List<StructValue>) reg.decodeTyped("struct:Pose2d[]", raw);
        assertEquals(2, a.size());
        List<double[]> poses = Poses.pose2dArray(a);
        assertEquals(4, poses.get(1)[1], 1e-12);
        assertEquals(180, poses.get(1)[2], 1e-9);
        assertEquals("[2 x Pose2d]", StructFormat.toText(a));
        assertNull(reg.decodeArray("Pose2d", new byte[25]));
        assertEquals(0, reg.decodeArray("Pose2d", new byte[0]).size());
    }

    @Test
    void decodesSwerveStates() {
        ByteBuffer b = buf(64);
        for (int i = 0; i < 4; i++) b.putDouble(i + 1.0).putDouble(Math.toRadians(i * 45.0));
        @SuppressWarnings("unchecked")
        List<StructValue> a = (List<StructValue>) reg.decodeTyped("struct:SwerveModuleState[]", b.array());
        List<double[]> s = Poses.swerveStates(a);
        assertEquals(4, s.size());
        assertEquals(3.0, s.get(2)[0], 1e-12);
        assertEquals(90.0, s.get(2)[1], 1e-9);
    }

    @Test
    void decodesChassisSpeedsAndRotation() {
        StructValue c = reg.decode("ChassisSpeeds", buf(24).putDouble(1).putDouble(-2).putDouble(0.5).array());
        assertArrayEquals(new double[] {1, -2, 0.5}, Poses.chassisSpeeds(c), 1e-12);
        StructValue r = reg.decode("Rotation2d", buf(8).putDouble(Math.PI).array());
        assertEquals(180, Poses.rotation2dDegrees(r), 1e-9);
    }

    @Test
    void packsBitfieldsOfTheSameWidthAndBoolsIntoOneUnit() {
        // The spec's rule: int8 and uint8 share a unit, and a bool joins an open unit when it fits.
        reg.addSchema("Packed", "int8 a:4;bool b:1;uint8 c:3;{x=1, y=2} uint8 axis");
        assertEquals(2, reg.size("Packed"));
        StructValue v = reg.decode("Packed", new byte[] {(byte) 0b1011_1110, 2}); // c=5, b=1, a=0xE=-2; axis=y
        assertEquals(-2L, v.get("a"));
        assertEquals(true, v.get("b"));
        assertEquals(5L, v.get("c"));
        assertEquals("y", v.get("axis"));
    }

    @Test
    void decodesBitfieldsEnumsArraysAndStrings() {
        reg.addSchema("Mix", "enum {off=0, on=1, auto=2} int8 mode;int8 lo:4;int8 hi:4;bool flag:1;bool f2:1;"
            + "uint16 n:12;int32 arr[2];char name[4];float f");
        // mode | lo=-3 (0xD) hi=5 -> 0x5D | bools 0b10 | n | arr | name | float
        ByteBuffer b = buf(1 + 1 + 1 + 2 + 8 + 4 + 4);
        b.put((byte) 2).put((byte) 0x5D).put((byte) 0b10).putShort((short) 0xABC)
            .putInt(7).putInt(-8).put((byte) 'h').put((byte) 'i').put((byte) 0).put((byte) 0).putFloat(1.5f);
        StructValue v = reg.decode("Mix", b.array());
        assertEquals("auto", v.get("mode"));
        assertEquals(-3L, v.get("lo"));
        assertEquals(5L, v.get("hi"));
        assertEquals(false, v.get("flag"));
        assertEquals(true, v.get("f2"));
        assertEquals(0xABCL, v.get("n"));
        assertEquals(List.of(7L, -8L), v.get("arr"));
        assertEquals("hi", v.get("name"));
        assertEquals(1.5, v.number("f"), 1e-12);
        assertEquals(b.capacity(), reg.size("Mix"));
    }

    @Test
    void unknownOrWrongLengthIsNull() {
        assertNull(reg.decodeTyped("struct:Unknown", new byte[8]));
        assertNull(reg.decodeTyped("double[]", new byte[8]));
        assertNull(reg.decode("Pose2d", new byte[23]));
        assertNull(Poses.pose2d(null));
    }

    @Test
    void versionChangesWhenSchemaAdded() {
        int v = reg.version();
        reg.addSchema("B", "int8 b");
        assertNotEquals(v, reg.version());
    }

    @Test
    void genericFormat() {
        StructValue v = reg.decode("SwerveModuleState", buf(16).putDouble(2).putDouble(Math.PI).array());
        assertEquals("SwerveModuleState(speed=2.00, angle=Rotation2d(180.0 deg))", StructFormat.toText(v));
    }

    @Test
    void anglesWrapIntoPlusMinus180() {
        assertEquals(171.2, Poses.wrapDegrees(1611.2), 1e-9);
        assertEquals(180, Poses.wrapDegrees(-180), 1e-9);
        assertEquals(-90, Poses.wrapDegrees(270), 1e-9);
        assertEquals(0, Poses.wrapDegrees(720), 1e-9);
    }
}
