package emberfx.replay;

import static org.junit.jupiter.api.Assertions.*;

import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.RawSubscriber;
import edu.wpi.first.networktables.StringSubscriber;
import edu.wpi.first.util.datalog.DataLogWriter;
import emberfx.structs.Poses;
import emberfx.structs.StructRegistry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LogIndexTest {
    @TempDir
    Path dir;
    private NetworkTableInstance inst;

    @BeforeEach
    void setUp() {
        inst = NetworkTableInstance.create();
    }

    @AfterEach
    void tearDown() {
        inst.close();
    }

    private static LogIndex load(Path... files) throws Exception {
        return LogIndex.load(List.of(files), p -> {});
    }

    private static LogIndex.Column col(LogIndex index, String topic) {
        for (LogIndex.Column c : index.columns) if (c.topic.equals(topic)) return c;
        throw new AssertionError("no column " + topic + " in " + index.topics());
    }

    @Test
    void mapsNames() {
        assertEquals("/SmartDashboard/x", LogIndex.topicName("NT:/SmartDashboard/x", false, null));
        assertEquals("/x", LogIndex.topicName("NT:x", false, null));
        assertEquals("/AdvantageKit/RealOutputs/Pose", LogIndex.topicName("/RealOutputs/Pose", false, null));
        assertEquals("/AdvantageKit/Drive/Gyro/Yaw", LogIndex.topicName("/Drive/Gyro/Yaw", true, null));
        assertEquals("/Drive/Gyro/Yaw", LogIndex.topicName("/Drive/Gyro/Yaw", false, null));
        assertEquals("/DriverStation/Enabled", LogIndex.topicName("DS:Enabled", false, null));
        assertEquals("/DriverStation/joystick0/axes", LogIndex.topicName("DS:joystick0/axes", false, null));
        assertEquals("/Phoenix6/TalonFX-18/Velocity", LogIndex.topicName("Phoenix6/TalonFX-18/Velocity", false, null));
        assertEquals("/DriveState/Pose", LogIndex.topicName("//DriveState/Pose", false, null));
        assertEquals("/systemTime", LogIndex.topicName("systemTime", false, null));
        assertEquals("/Phoenix6/rio/TalonFX-1/Velocity", LogIndex.topicName("Phoenix6/TalonFX-1/Velocity", false, "rio"));
        assertEquals("/DriveState/Pose", LogIndex.topicName("DriveState/Pose", false, "rio"));

        assertEquals("struct:Pose2d", LogIndex.schemaName(".schema/struct:Pose2d"));
        assertEquals("struct:Pose2d", LogIndex.schemaName("/.schema/struct:Pose2d"));
        assertEquals("struct:Pose2d", LogIndex.schemaName("NT:/.schema/struct:Pose2d"));
        assertEquals("proto:Pose2d", LogIndex.schemaName("/AdvantageKit/.schema/proto:Pose2d"));
        assertNull(LogIndex.schemaName("NT:/SmartDashboard/x"));
        assertNull(LogIndex.schemaName(".schema/"));
    }

    @Test
    void storesColumnsAndDropsRepeats() throws Exception {
        Path f = dir.resolve("a.wpilog");
        try (DataLogWriter log = new DataLogWriter(f.toString())) {
            int d = log.start("NT:/d", "double", "", 1);
            int fl = log.start("NT:/f", "float", "", 1);
            int i = log.start("NT:/i", "int64", "", 1);
            int b = log.start("NT:/b", "boolean", "", 1);
            int s = log.start("NT:/s", "string", "", 1);
            int a = log.start("NT:/a", "double[]", "", 1);
            log.appendDouble(d, 1.5, 10);
            log.appendDouble(d, 1.5, 20); // repeat: dropped
            log.appendDouble(d, 2.5, 30);
            log.appendFloat(fl, 0.25f, 10);
            log.appendInteger(i, 7, 10);
            log.appendInteger(i, 8, 20);
            log.appendBoolean(b, true, 10);
            log.appendBoolean(b, false, 40);
            log.appendString(s, "hi", 10);
            log.appendString(s, "yo", 20);
            log.appendString(s, "hi", 30);
            log.appendDoubleArray(a, new double[] {1, 2}, 10);
            log.appendDoubleArray(a, new double[] {1, 2}, 20); // repeat: dropped
            log.appendDouble(d, 3.5, 25); // out of order: sorted in
            int reused = log.start("NT:/r", "int64", "", 1);
            log.appendInteger(reused, 1, 10);
            log.finish(reused, 11);
            reused = log.start("NT:/r", "int64", "", 50);
            log.appendInteger(reused, 2, 60);
        }
        LogIndex index = load(f);
        assertEquals(7, index.entryCount());
        assertEquals(List.of("a.wpilog"), index.sourceNames());
        assertEquals(10, index.startMicros());
        assertEquals(60, index.endMicros());

        LogIndex.Column d = col(index, "/d");
        assertArrayEquals(new long[] {10, 25, 30}, d.ts);
        assertArrayEquals(new double[] {1.5, 3.5, 2.5}, d.d);
        assertEquals(0.25, col(index, "/f").d[0]);
        assertArrayEquals(new long[] {7, 8}, col(index, "/i").l);
        assertArrayEquals(new boolean[] {true, false}, col(index, "/b").b);
        LogIndex.Column s = col(index, "/s");
        assertEquals(3, s.size);
        assertSame(s.o[0], s.o[2], "strings interned per entry");
        assertEquals(1, col(index, "/a").size);
        assertArrayEquals(new long[] {1, 2}, col(index, "/r").l);
    }

    @Test
    void seeksForwardAndBack() throws Exception {
        Path f = dir.resolve("seek.wpilog");
        try (DataLogWriter log = new DataLogWriter(f.toString())) {
            int d = log.start("NT:/v", "double", "", 0);
            for (int k = 0; k < 1000; k++) log.appendDouble(d, k, 1000 + k * 100L);
            int s = log.start("NT:/late", "string", "", 0);
            log.appendString(s, "here", 50_000);
        }
        LogIndex index = load(f);
        DoubleSubscriber v = inst.getDoubleTopic("/v").subscribe(-1);
        StringSubscriber late = inst.getStringTopic("/late").subscribe("none");
        try (Replayer r = new Replayer(index, inst)) {
            r.seek(500);
            assertFalse(inst.getTopic("/v").exists(), "nothing before the first value");
            for (long t = 1000; t <= 101_000; t += 37) { // every step forward, short and long jumps
                r.seek(t);
                assertEquals(Math.min(999, (t - 1000) / 100), v.get(), 0, "at " + t);
            }
            assertEquals("here", late.get());
            r.seek(20_050); // back
            assertEquals(190, v.get());
            assertFalse(inst.getTopic("/late").exists(), "unpublished before its first value");
            r.seek(1_000_000); // far forward
            assertEquals(999, v.get());
            r.seek(0);
            assertFalse(inst.getTopic("/v").exists());
            r.seek(1050);
            assertEquals(0, v.get());
        }
        assertFalse(inst.getTopic("/v").exists(), "close unpublishes");
    }

    @Test
    void mergesFiles() throws Exception {
        Path a = dir.resolve("a.wpilog"), b = dir.resolve("b.wpilog");
        try (DataLogWriter log = new DataLogWriter(a.toString())) {
            int e = log.start("DS:Enabled", "boolean", "", 0);
            log.appendBoolean(e, false, 10);
            log.appendBoolean(e, true, 30);
            int x = log.start("NT:/only/a", "double", "", 0);
            log.appendDouble(x, 1, 5);
            int t = log.start("NT:/clash", "double", "", 0);
            log.appendDouble(t, 1, 5);
        }
        try (DataLogWriter log = new DataLogWriter(b.toString())) {
            int e = log.start("DS:Enabled", "boolean", "", 0);
            log.appendBoolean(e, false, 10); // same record as in a: collapses
            log.appendBoolean(e, true, 20);
            log.appendBoolean(e, false, 40);
            int t = log.start("NT:/clash", "string", "", 0); // other type: skipped
            log.appendString(t, "x", 5);
        }
        LogIndex index = load(a, b);
        assertEquals(List.of("a.wpilog", "b.wpilog"), index.sourceNames());
        LogIndex.Column e = col(index, "/DriverStation/Enabled");
        assertArrayEquals(new long[] {10, 20, 40}, e.ts); // the true at 30 repeats the true at 20
        assertArrayEquals(new boolean[] {false, true, false}, e.b);
        assertEquals(3, index.entryCount());
        assertEquals("double", col(index, "/clash").logType);
        assertEquals(5, index.startMicros());
        assertEquals(40, index.endMicros());
    }

    @Test
    void keepsHootBusesApart() throws Exception {
        Path rio = dir.resolve("rio.wpilog"), canivore = dir.resolve("drive.wpilog");
        try (DataLogWriter log = new DataLogWriter(rio.toString(), "roboRIO Native CAN Bus")) {
            log.appendDouble(log.start("Phoenix6/TalonFX-1/Velocity", "double", "", 1), 1, 10);
            log.appendString(log.start("RobotMode", "string", "", 1), "Disabled", 10);
        }
        try (DataLogWriter log = new DataLogWriter(canivore.toString(), "Drivetrain")) {
            log.appendDouble(log.start("Phoenix6/TalonFX-1/Velocity", "double", "", 1), 2, 10);
            log.appendString(log.start("RobotMode", "string", "", 1), "Disabled", 10);
        }
        LogIndex index = load(rio, canivore);
        assertEquals(1, col(index, "/Phoenix6/rio/TalonFX-1/Velocity").d[0]);
        assertEquals(2, col(index, "/Phoenix6/Drivetrain/TalonFX-1/Velocity").d[0]);
        assertEquals(1, col(index, "/RobotMode").size, "the robot's own signals are in both files once");
    }

    @Test
    void replaysStructsWithTheirSchema() throws Exception {
        Path f = dir.resolve("pose.wpilog");
        try (DataLogWriter log = new DataLogWriter(f.toString())) {
            log.addSchema("struct:Translation2d", "structschema", "double x;double y");
            log.addSchema("struct:Rotation2d", "structschema", "double value");
            log.addSchema("struct:Pose2d", "structschema", "Translation2d translation;Rotation2d rotation");
            int p = log.start("DriveState/Pose", "struct:Pose2d", "", 0);
            byte[] raw = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putDouble(1.5).putDouble(2.5).putDouble(Math.PI).array();
            log.appendRaw(p, raw, 100);
        }
        LogIndex index = load(f);
        assertEquals(1, index.entryCount(), "schemas are not topics: " + index.topics());
        assertEquals(3, index.schemas().size());

        StructRegistry reg = new StructRegistry();
        reg.listen(inst);
        RawSubscriber pose = inst.getRawTopic("/DriveState/Pose").subscribe("struct:Pose2d", new byte[0]);
        try (Replayer r = new Replayer(index, inst)) {
            r.seek(100);
            assertEquals("struct:Pose2d", inst.getTopic("/DriveState/Pose").getTypeString());
            long deadline = System.currentTimeMillis() + 2000;
            while (reg.size("Pose2d") < 0 && System.currentTimeMillis() < deadline) Thread.sleep(10);
            double[] xyDeg = Poses.pose2d(reg.decode("Pose2d", pose.get()));
            assertEquals(1.5, xyDeg[0], 1e-12);
            assertEquals(2.5, xyDeg[1], 1e-12);
            assertEquals(180, xyDeg[2], 1e-9);
        }
    }

    @Test
    void phasesFromFmsControlData() throws Exception {
        Path f = dir.resolve("fms.wpilog");
        try (DataLogWriter log = new DataLogWriter(f.toString())) {
            int w = log.start("NT:/FMSInfo/FMSControlData", "int64", "", 0);
            log.appendInteger(w, 0x20, 1_000_000);   // DS attached, disabled
            log.appendInteger(w, 0x33, 2_000_000);   // FMS, auto, enabled
            log.appendInteger(w, 0x32, 17_000_000);  // auto but disabled
            log.appendInteger(w, 0x31, 20_000_000);  // teleop
            log.appendInteger(w, 0x35, 30_000_000);  // test
            log.appendInteger(w, 0x30, 155_000_000);
            log.appendInteger(w, 0x20, 156_000_000); // still disabled: merged
            int x = log.start("NT:/x", "double", "", 0);
            log.appendDouble(x, 1, 160_000_000);
        }
        assertEquals(List.of(
            new LogIndex.Phase(1_000_000, 2_000_000, "Disabled"),
            new LogIndex.Phase(2_000_000, 17_000_000, "Auto"),
            new LogIndex.Phase(17_000_000, 20_000_000, "Disabled"),
            new LogIndex.Phase(20_000_000, 30_000_000, "Teleop"),
            new LogIndex.Phase(30_000_000, 155_000_000, "Test"),
            new LogIndex.Phase(155_000_000, 160_000_000, "Disabled")), load(f).phases());
    }

    @Test
    void phasesFromDriverStationBooleans() throws Exception {
        Path f = dir.resolve("ds.wpilog");
        try (DataLogWriter log = new DataLogWriter(f.toString())) {
            int en = log.start("DS:Enabled", "boolean", "", 0);
            int au = log.start("DS:Autonomous", "boolean", "", 0);
            log.appendBoolean(en, false, 1);
            log.appendBoolean(au, true, 5);
            log.appendBoolean(en, true, 10);
            log.appendBoolean(en, false, 20);
            log.appendBoolean(au, false, 21);
            log.appendBoolean(en, true, 25);
            log.appendBoolean(en, false, 40);
        }
        assertEquals(List.of(
            new LogIndex.Phase(1, 10, "Disabled"),
            new LogIndex.Phase(10, 20, "Auto"),
            new LogIndex.Phase(20, 25, "Disabled"),
            new LogIndex.Phase(25, 40, "Teleop")), load(f).phases());
    }

    @Test
    void spansMergeChangesAtOneTime() {
        List<long[]> changes = new ArrayList<>();
        changes.add(new long[] {0, 0});
        changes.add(new long[] {10, 1});
        changes.add(new long[] {10, 0}); // replaced at the same time
        changes.add(new long[] {20, 2});
        assertEquals(List.of(new LogIndex.Phase(0, 20, "Disabled"), new LogIndex.Phase(20, 30, "Teleop")),
            LogIndex.spans(changes, 30));
        assertEquals(List.of(), LogIndex.spans(List.of(), 30));
    }

    @Test
    void cancelsAndReportsBadFiles() throws Exception {
        Path f = dir.resolve("big.wpilog");
        try (DataLogWriter log = new DataLogWriter(f.toString())) {
            int d = log.start("NT:/v", "double", "", 0);
            for (int k = 0; k < 70_000; k++) log.appendDouble(d, k, k + 1);
        }
        List<Double> progress = new ArrayList<>();
        LogIndex.load(List.of(f), progress::add);
        assertEquals(1.0, progress.get(progress.size() - 1));

        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () -> load(f));
        } finally {
            Thread.interrupted();
        }

        Path bad = dir.resolve("bad.wpilog");
        java.nio.file.Files.writeString(bad, "not a log at all");
        IOException e = assertThrows(IOException.class, () -> load(bad));
        assertTrue(e.getMessage().contains("bad.wpilog"), e.getMessage());
        assertThrows(IOException.class, () -> load(dir.resolve("missing.wpilog")));
    }
}
