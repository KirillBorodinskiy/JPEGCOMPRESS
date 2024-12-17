module image.compress.imagecompress {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;


    opens image.compress.imagecompress to javafx.fxml;
    exports image.compress.imagecompress;
}