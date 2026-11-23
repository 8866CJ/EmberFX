package frcdash.app;

import edu.wpi.first.networktables.MultiSubscriber;
import edu.wpi.first.networktables.NetworkTableEvent;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.PubSubOption;
import edu.wpi.first.util.datalog.DataLogBackgroundWriter;
import frcdash.NT;
import frcdash.config.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Records everything the robot publishes to a .wpilog file (opens in AdvantageScope).
 * A new file starts on every connection, so each session is its own file.
 */
public final class SessionLog {
    public static final Path DIR = Config.DIR.resolve("logs");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final double FLUSH_PERIOD_SECONDS = 0.25;
    private static final System.Logger LOG = System.getLogger(SessionLog.class.getName());

    private static MultiSubscriber everything;
    private static DataLogBackgroundWriter writer;
    private static int entryLogger, connectionLogger;
    private static volatile String currentFile;
    private static String prefix = "frcdash";

    private SessionLog() {}

    /** Start logging on every connection. Call once at startup, before connecting. */
    public static synchronized void install(String filePrefix) {
        prefix = filePrefix;
        // sendAll: receive every value the robot publishes, not just the latest one per update.
        everything = new MultiSubscriber(NT.instance(), new String[] {""},
            PubSubOption.sendAll(true), PubSubOption.keepDuplicates(true));
        NT.instance().addConnectionListener(true, event -> {
            if (event.is(NetworkTableEvent.Kind.kConnected)) start();
            else if (event.is(NetworkTableEvent.Kind.kDisconnected)) stop();
        });
    }

    /** Name of the file being written, or null when not connected. */
    public static String currentFile() {
        return currentFile;
    }

    /** Finish the current file. Call on exit. */
    public static synchronized void shutdown() {
        stop();
        if (everything != null) everything.close();
    }

    private static synchronized void start() {
        stop();
        try {
            Files.createDirectories(DIR);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.ERROR, "Can't create " + DIR + "; not logging this session", e);
            return;
        }
        String name = prefix + "_" + LocalDateTime.now().format(FILE_TIME) + ".wpilog";
        writer = new DataLogBackgroundWriter(DIR.toString(), name, FLUSH_PERIOD_SECONDS);
        entryLogger = NT.instance().startEntryDataLog(writer, "", "NT:");
        connectionLogger = NT.instance().startConnectionDataLog(writer, "NTConnection");
        currentFile = name;
    }

    private static synchronized void stop() {
        if (writer == null) return;
        NetworkTableInstance.stopEntryDataLog(entryLogger);
        NetworkTableInstance.stopConnectionDataLog(connectionLogger);
        writer.close();
        writer = null;
        currentFile = null;
    }
}
