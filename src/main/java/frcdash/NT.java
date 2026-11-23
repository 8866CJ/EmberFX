package frcdash;

import edu.wpi.first.networktables.BooleanPublisher;
import edu.wpi.first.networktables.BooleanSubscriber;
import edu.wpi.first.networktables.DoublePublisher;
import edu.wpi.first.networktables.DoubleSubscriber;
import edu.wpi.first.networktables.NetworkTableEvent;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.networktables.StringPublisher;

import java.util.function.Consumer;

/**
 * The dashboard's NetworkTables connection, plus short helpers for the subscribers and publishers
 * tiles and modules use most. Anything else is available through {@link #instance()}.
 */
public final class NT {
    private static final NetworkTableInstance INSTANCE = NetworkTableInstance.getDefault();
    private static final String CLIENT_NAME = "FRC-Dashboard";

    private NT() {}

    /** The NetworkTables instance, for anything the helpers below don't cover. */
    public static NetworkTableInstance instance() {
        return INSTANCE;
    }

    /** Connect to a robot or simulator at this address, dropping any current connection. */
    public static void startClient(String server) {
        INSTANCE.stopClient();
        INSTANCE.setServer(server);
        INSTANCE.startClient4(CLIENT_NAME);
    }

    public static void stopClient() {
        INSTANCE.stopClient();
    }

    public static boolean isConnected() {
        return INSTANCE.isConnected();
    }

    /** Called with true on connect and false on disconnect, and right away if already connected. */
    public static void onConnectionChange(Consumer<Boolean> listener) {
        INSTANCE.addConnectionListener(true, event -> listener.accept(event.is(NetworkTableEvent.Kind.kConnected)));
    }

    public static DoubleSubscriber subDouble(String topic, double defaultValue) {
        return INSTANCE.getDoubleTopic(topic).subscribe(defaultValue);
    }

    public static BooleanSubscriber subBool(String topic, boolean defaultValue) {
        return INSTANCE.getBooleanTopic(topic).subscribe(defaultValue);
    }

    public static DoublePublisher pubDouble(String topic) {
        return INSTANCE.getDoubleTopic(topic).publish();
    }

    public static BooleanPublisher pubBool(String topic) {
        return INSTANCE.getBooleanTopic(topic).publish();
    }

    public static StringPublisher pubString(String topic) {
        return INSTANCE.getStringTopic(topic).publish();
    }
}
