module scalemonitor {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fazecast.jSerialComm;


    opens com.ridwanharts.scalemonitor.controller to javafx.fxml;
    opens com.ridwanharts.scalemonitor to javafx.fxml;
    exports com.ridwanharts.scalemonitor;
}