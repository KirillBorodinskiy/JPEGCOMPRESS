module image.compress.imagecompress {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires java.logging;


    opens image.compress.imagecompress to javafx.fxml;
    exports image.compress.imagecompress;
}