/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.ridwanharts.scalemonitor.controller;

import com.ridwanharts.scalemonitor.service.SerialService;
import com.ridwanharts.scalemonitor.util.WeightParser;
import java.util.List;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;

/**
 *
 * @author ridwan
 */
public class ScaleController {

    @FXML private ComboBox<String> portBox;
    @FXML private ComboBox<Integer> baudBox;
    @FXML private Button btnRefresh;
    @FXML private Button btnConnect;
    @FXML private Button btnDisconnect;
    @FXML private TextArea rawArea;
    @FXML private Label weightLabel;
    @FXML private Label statusLabel;
    @FXML private CheckBox chkCR;
    @FXML private CheckBox chkLF;
    @FXML private ChoiceBox<String> formatChoice;
    @FXML private ComboBox<String> parseModeBox;
    @FXML private Button btnClear;

    private SerialService serialService;
    private final WeightParser parser = new WeightParser();

    @FXML
    private void initialize() {
        serialService = new SerialService();

        // fill baud rates & format options
        baudBox.getItems().addAll(9600, 19200, 38400, 57600, 115200);
        baudBox.setValue(9600);

        formatChoice.getItems().addAll("8N1");
        formatChoice.setValue("8N1");

        // fill parse mode options
        parseModeBox.getItems().addAll("RAW_DEBUG", "LINE_BASED", "FRAME_PARSER");
        parseModeBox.setValue("RAW_DEBUG");
        parseModeBox.setOnAction(e -> changeParseModeFromUI());

        updatePorts();

        btnRefresh.setOnAction(e -> updatePorts());
        btnConnect.setOnAction(e -> connect());
        btnDisconnect.setOnAction(e -> disconnect());
        btnClear.setOnAction(e -> {
            rawArea.clear();
            weightLabel.setText("0.0 kg");
        });
    }

    private void updatePorts() {
        List<String> ports = serialService.listPorts();
        portBox.getItems().setAll(ports);
        status("Ports refreshed: " + ports.size());
    }

    private void connect() {
        String port = portBox.getValue();
        int baud = baudBox.getValue();
        if (port == null || port.isEmpty()) {
            status("Select a COM port first.");
            return;
        }

        btnConnect.setDisable(true);
        status("Opening " + port + " @ " + baud + "...");
        serialService.open(port, baud, (raw) -> {
            // data callback from background thread
            Platform.runLater(() -> {
                rawArea.appendText(raw + System.lineSeparator());
                String w = parser.parseWeight(raw);
                if (w != null && !w.isEmpty()) {
                    weightLabel.setText(w + " kg");
                }
            });
        }, error -> {
            Platform.runLater(() -> status("Error: " + error));
            System.err.println("Serial error: " + error);
        });

        btnDisconnect.setDisable(false);
        status("Connected to " + port);
    }

    private void disconnect() {
        serialService.close();
        btnConnect.setDisable(false);
        btnDisconnect.setDisable(true);
        status("Disconnected");
    }

    private void changeParseModeFromUI() {
        String selectedMode = parseModeBox.getValue();
        if (selectedMode != null) {
            try {
                SerialService.ParseMode mode = SerialService.ParseMode.valueOf(selectedMode);
                serialService.setParseMode(mode);
                status("Parse mode changed to: " + selectedMode);
            } catch (IllegalArgumentException ex) {
                status("Invalid parse mode: " + selectedMode);
            }
        }
    }

    private void status(String text) {
        statusLabel.setText(text);
    }
}