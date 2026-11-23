package frcdash.ui;

import frcdash.NT;
import frcdash.config.Config;
import frcdash.topics.FoundTopic;
import frcdash.topics.Topics;
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
    private TopicBrowser() {}

    public static Optional<FoundTopic> pick() {
        List<FoundTopic> topics = Topics.all();
        TopicList list = new TopicList(false);
        list.setTopics(topics);
        list.listView().setPrefSize(640, 420);

        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("Pick a topic");
        d.setHeaderText(summary(topics));
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.getDialogPane().setContent(new VBox(8, list,
            new Label("Tip: use Setup > Link robot project to see topics before the robot is on.")));
        list.onChoose(t -> {
            d.setResult(ButtonType.OK);
            d.close();
        });
        list.focusSearch();
        return d.showAndWait().filter(b -> b == ButtonType.OK).map(b -> list.selected());
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
