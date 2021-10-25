package org.daltontf1212;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClipZoomFxApp extends Application {

    private MainController controller;

    @Override
    public final void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/Main.fxml"));

        Parent root = loader.load();

        controller = loader.getController();

        controller.start(primaryStage);

        Scene scene = new Scene(root, 1300, 775);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
