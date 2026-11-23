package frcdash;

/** Entry point for the .exe and fat jar. JavaFX refuses to start from a classpath jar when main() is on the Application class. */
public final class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
