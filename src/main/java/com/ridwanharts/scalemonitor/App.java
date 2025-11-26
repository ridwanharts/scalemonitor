package com.ridwanharts.scalemonitor;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


/**
 * JavaFX App
 */
public class App extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/fxml/ScaleView.fxml"));
        Scene scene = new Scene(root);
        stage.setTitle("Scale Monitor");
        stage.setScene(scene);
        stage.setMinWidth(600);
        stage.setMinHeight(420);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}