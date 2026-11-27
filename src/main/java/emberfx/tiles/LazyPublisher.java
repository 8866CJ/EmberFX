package emberfx.tiles;

import edu.wpi.first.networktables.Publisher;
import emberfx.NT;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A publisher for a control: created the first time the driver uses it, and dropped when the robot
 * disconnects, so the next use after a reconnect creates a fresh one.
 *
 * Dropping it matters. On reconnect NetworkTables re-sends the last value of every open publisher, which
 * would silently undo a change made in robot code since the driver last touched the control (a new PID
 * default, a preference, a toggle the robot turned off). Wrap it in the tile's track(...) so it closes
 * with the tile.
 *
 * <pre>
 * private final LazyPublisher&lt;BooleanPublisher&gt; pub = track(new LazyPublisher&lt;&gt;(() -&gt; NT.pubBool(config.topic())));
 * ... pub.get().set(true); // in the control's action
 * </pre>
 */
public final class LazyPublisher<P extends Publisher> implements AutoCloseable {
    private static final Set<LazyPublisher<?>> OPEN = ConcurrentHashMap.newKeySet();

    static {
        // Runs on NetworkTables' listener thread; get() and drop() are synchronized against it.
        NT.onConnectionChange(connected -> {
            if (!connected) OPEN.forEach(LazyPublisher::drop);
        });
    }

    /**
     * Drop every control's publisher now, as on a disconnect. The dashboard calls this when a replay ends,
     * so a control pressed during the replay isn't re-sent to the robot on reconnect.
     */
    public static void dropAll() {
        OPEN.forEach(LazyPublisher::drop);
    }

    private final Supplier<P> create;
    private P pub;

    public LazyPublisher(Supplier<P> create) {
        this.create = create;
    }

    /** The publisher, created now if there isn't one. Call it when the driver uses the control. */
    public synchronized P get() {
        if (pub == null) {
            pub = create.get();
            OPEN.add(this);
        }
        return pub;
    }

    private synchronized void drop() {
        if (pub != null) {
            pub.close();
            pub = null;
        }
        OPEN.remove(this);
    }

    @Override
    public void close() {
        drop();
    }
}
