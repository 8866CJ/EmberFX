package frcdash;

import edu.wpi.first.networktables.*;
import edu.wpi.first.util.datalog.DataLogBackgroundWriter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public final class NT {
    private static final NetworkTableInstance inst = NetworkTableInstance.getDefault();
    public static final Path LOG_DIR = Paths.get(System.getProperty("user.home"), ".frcdash", "logs");

    // sendAll: get every value the robot publishes, not just the latest one per update.
    private static final MultiSubscriber everything =
        new MultiSubscriber(inst, new String[] {""}, PubSubOption.sendAll(true), PubSubOption.keepDuplicates(true));

    private static DataLogBackgroundWriter log;
    private static int entryLogger, connLogger;
    private static volatile String logFile = null;
    private static volatile String logPrefix = "frcdash";

    static {
        // One .wpilog per robot connection, so each session is its own file in AdvantageScope.
        inst.addConnectionListener(true, event -> {
            if (event.is(NetworkTableEvent.Kind.kConnected)) startLog();
            else if (event.is(NetworkTableEvent.Kind.kDisconnected)) stopLog();
        });
    }

    private NT() {}

    private static synchronized void startLog() {
        stopLog();
        LOG_DIR.toFile().mkdirs();
        String name = logPrefix + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".wpilog";
        log = new DataLogBackgroundWriter(LOG_DIR.toString(), name, 0.25);
        entryLogger = inst.startEntryDataLog(log, "", "NT:");
        connLogger = inst.startConnectionDataLog(log, "NTConnection");
        logFile = name;
    }

    private static synchronized void stopLog() {
        if (log == null) return;
        NetworkTableInstance.stopEntryDataLog(entryLogger);
        NetworkTableInstance.stopConnectionDataLog(connLogger);
        log.close();
        log = null;
        logFile = null;
    }

    /** File currently being written, or null when not connected. */
    public static String currentLogFile() {
        return logFile;
    }

    /** Start of each .wpilog file name, e.g. "frc254". Set before connecting. */
    public static void setLogPrefix(String prefix) {
        logPrefix = prefix;
    }

    public static void startClient(String server) {
        inst.stopClient();
        inst.setServer(server);
        inst.startClient4("FRC-Dashboard");
    }

    public static void shutdown() {
        stopLog();
        everything.close();
        inst.stopClient();
    }

    /** The NetworkTables instance, for anything the helpers below don't cover. */
    public static NetworkTableInstance instance() {
        return inst;
    }

    public static boolean isConnected() {
        return inst.isConnected();
    }

    public static DoubleSubscriber subDouble(String topic, double defaultVal) {
        return inst.getDoubleTopic(topic).subscribe(defaultVal);
    }

    public static BooleanSubscriber subBool(String topic, boolean defaultVal) {
        return inst.getBooleanTopic(topic).subscribe(defaultVal);
    }

    public static BooleanPublisher pubBool(String topic) {
        return inst.getBooleanTopic(topic).publish();
    }

    public static StringPublisher pubString(String topic) {
        return inst.getStringTopic(topic).publish();
    }

    public static DoublePublisher pubDouble(String topic) {
        return inst.getDoubleTopic(topic).publish();
    }

    public static void onConnectionChange(Consumer<Boolean> cb) {
        inst.addConnectionListener(true, event -> cb.accept(event.is(NetworkTableEvent.Kind.kConnected)));
    }
}
