package emberfx.ui;

import emberfx.NT;
import emberfx.config.Config;
import emberfx.topics.FoundTopic;
import emberfx.topics.Topics;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * Pick a topic from everything the robot publishes and everything found in the linked robot code.
 * Type to filter; double-click or OK to choose.
 */
public final class TopicBrowser {
    /** What {@link #pick()} returns when the user chose to type the topic themselves. */
    public static final FoundTopic TYPE_IT = new FoundTopic("", "", "", false);

    private TopicBrowser() {}

    /** The picked topic, {@link #TYPE_IT} for "Type a topic...", or empty when cancelled. */
    public static Optional<FoundTopic> pick() {
        List<FoundTopic> topics = Topics.all();
        TopicList list = new TopicList(false);
        list.setTopics(topics);
        list.listView().setPrefSize(640, 420);

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Pick a topic");
        d.setHeaderText(summary(topics));
        ButtonType typeIn = new ButtonType("Type a topic...", ButtonBar.ButtonData.LEFT);
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, typeIn, ButtonType.CANCEL);
        // Without a picked row, OK (and Enter in the search box) does nothing instead of acting like Cancel.
        d.getDialogPane().lookupButton(ButtonType.OK).disableProperty()
            .bind(list.listView().getSelectionModel().selectedItemProperty().isNull());
        d.getDialogPane().setContent(new VBox(8, list,
            new Label("Tip: use Menu > Link robot project to see topics before the robot is on.")));
        list.onChoose(t -> {
            d.setResult(ButtonType.OK);
            d.close();
        });
        list.focusSearch();
        return d.showAndWait().flatMap(b -> b == ButtonType.OK ? Optional.ofNullable(list.selected())
            : b == typeIn ? Optional.of(TYPE_IT) : Optional.empty());
    }

    /** "12 on the robot, 3 more found only in your code." */
    static String summary(List<FoundTopic> topics) {
        long live = topics.stream().filter(t -> t.source().startsWith("robot")).count();
        String robot = NT.isConnected() ? live + " on the robot" : "Robot not connected";
        String code = Config.robotProject().isEmpty() ? "no robot project linked"
            : (topics.size() - live) + " more found only in your code";
        return robot + ", " + code + ".";
    }
}
