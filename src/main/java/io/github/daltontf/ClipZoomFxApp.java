package io.github.daltontf;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;

public class ClipZoomFxApp extends Application {

    private MainController controller;

    @Override
    public final void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/Main.fxml"));

        Parent root = loader.load();

        controller = loader.getController();

        controller.start(primaryStage);

        Thread.currentThread().setUncaughtExceptionHandler((thread, throwable) -> {
            System.out.println("UI encountered unrecoverable exception: "+ throwable.getMessage());
            File file = new File("clipzoomfx_dump_project.json");
            controller.saveProjectFile(file);
            System.out.println("Saved  project file as " + file.getAbsolutePath() + " for recovery");
            System.exit(1);
        });

        Scene scene = new Scene(root, 1300, 775);

        scene.getStylesheets().add(getClass().getResource("/Main.css").toExternalForm());

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
