package image.compress.imagecompress;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.plugins.jpeg.JPEGQTable;

public class ImageCompressionApp extends Application {

    // CONSTANTS:
    private static final ImageView originalImageView = new ImageView();
    private static final ImageView compressedImageView = new ImageView();
    private static BufferedImage bufferedImage;
    private static final int DEFAULT_COMPRESSION_LEVEL = 80;
    private static final int BLOCK_SIZE = 8;
    private static final String LUMINANCE = "Luminance";
    private static final String CHROMINANCE = "Chrominance";
    private static final double[][] COSINES = precomputeCosines();

    private static double[][] precomputeCosines() {
        double[][] cosines = new double[BLOCK_SIZE][BLOCK_SIZE];

        for (int m = 0; m < BLOCK_SIZE; m++) {
            for (int p = 0; p < BLOCK_SIZE; p++) {
                cosines[m][p] = Math.cos(((2 * m + 1) * p * Math.PI) / (2 * BLOCK_SIZE));
            }
        }
        return cosines;
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Image Compression Tool");

        // UI Components
        Button loadButton = new Button("Load Image");
        Button saveButton = new Button("Save Compressed Image");
        Slider compressionSlider = new Slider(1, 99, DEFAULT_COMPRESSION_LEVEL);
        Label compressionLabel = new Label("Compression Level: " + DEFAULT_COMPRESSION_LEVEL + "%");

        // Layout
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(10);

        grid.add(loadButton, 0, 0);
        grid.add(compressionLabel, 1, 0);
        grid.add(compressionSlider, 1, 1);
        grid.add(saveButton, 2, 0);
        grid.add(originalImageView, 0, 2);
        grid.add(compressedImageView, 1, 2);

        // Event Handlers
        loadButton.setOnAction(e -> loadImage(primaryStage));
        compressionSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            compressionLabel.setText("Compression Level: " + newVal.intValue() + "%");
            if (bufferedImage != null) {
                compressImage(newVal.intValue());
            }
        });
        // Scene
        Scene scene = new Scene(grid, 1280, 720);
        primaryStage.setScene(scene);
        primaryStage.show();
    }


    private void loadImage(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            try {
                BufferedImage bufferedImage = ImageIO.read(new File(selectedFile.toURI().toString()));

                Image image = SwingFXUtils.toFXImage(bufferedImage, null);
                originalImageView.setImage(image);
                int IMAGE_WIDTH = 400;
                originalImageView.setFitWidth(IMAGE_WIDTH);
                originalImageView.setPreserveRatio(true);
                compressImage(DEFAULT_COMPRESSION_LEVEL);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void compressImage(int compressionLevel) {
        /*
        1.Convert RGB to YCrCb
        2.Crominance downsapling
        3.Discrete Cosine Transform
        4.Quantization
        5.RLE and huffman
         */
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();
        double[][] YValue = new double[height][width];
        double[][] CbValue = new double[height][width];
        double[][] CrValue = new double[height][width];
        // Step 1: Convert RGB to YCrCb
        // The result will be inside the arrays
        convertToYCbCr(bufferedImage, YValue, CbValue, CrValue);

        // Step 2: Crominance downsapling
        //THE SIZE OF AN ARRAY IS 4 TIMES SMALLER
        double[][] DCbValue = downsample(CbValue, height, width);
        double[][] DCrValue = downsample(CrValue, height, width);

        // Step 3,4: DCT, Quantization
        DCT(YValue, LUMINANCE, compressionLevel);
        DCT(DCbValue, CHROMINANCE, compressionLevel);
        DCT(DCrValue, CHROMINANCE, compressionLevel);
    }

    private void convertToYCbCr(BufferedImage input, double[][] YValue, double[][] CbValue, double[][] CrValue) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = input.getRGB(x, y);
                Color color = new Color(rgb);

                int red = color.getRed();
                int green = color.getGreen();
                int blue = color.getBlue();

                //Y first because Y is first
                YValue[y][x] = 0.299 * red + 0.587 * green + 0.114 * blue;
                CbValue[y][x] = 128 - 0.168736 * red - 0.331264 * green + 0.5 * blue;
                CrValue[y][x] = 128 + 0.5 * red - 0.418688 * green - 0.081312 * blue;

            }
        }
    }

    private double[][] downsample(double[][] Color, int height, int width) {
        int newHeight = height / 2;
        int newWidth = width / 2;

        double[][] DownsampledColor = new double[newHeight][newWidth];

        for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {

                //This ensures that we are not out of bounds
                double firstElement = Color[y][x];
                double secondElement = (x + 1 < width) ? Color[y][x + 1] : 0;
                double thirdElement = (y + 1 < height) ? Color[y + 1][x] : 0;
                double fourthElement = (y + 1 < height && x + 1 < width) ? Color[y + 1][x + 1] : 0;


                DownsampledColor[y / 2][x / 2] = (firstElement + secondElement + thirdElement + fourthElement) / 4;
            }
        }

        return DownsampledColor;
    }

    private void DCT(double[][] input, String type, int compressionLevel) {
        // Formula from
        // https://www.mathworks.com/help/images/discrete-cosine-transform.html


        //Blocks of 8x8
        for (int i = 0; i < input.length; i += BLOCK_SIZE) {// HEIGHT
            for (int j = 0; j < input[0].length; j += BLOCK_SIZE) {// WIDTH
                double[][] block = extractBlocks(input, i, j);
                double[][] DCTBlock = performDCT(block);


                // Step 4: Quantization
                double[][] QBlock = performQuantization(DCTBlock, type, compressionLevel);
            }
        }
    }

    private double[][] performQuantization(double[][] block, String type, float compressionLevel) {
        double[][] temp_block = new double[BLOCK_SIZE][BLOCK_SIZE];

        int[] quantizationValues = calculateQuantizationTable(type, compressionLevel);

        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                //round it to the nearest int
                temp_block[i][j] = Math.round(block[i][j] / quantizationValues[i * BLOCK_SIZE + j]);
            }
        }
        return temp_block;
    }

    private static int[] calculateQuantizationTable(String type, float compressionLevel) {
        JPEGQTable Table;
        if (type.equals(LUMINANCE)) {
            Table = JPEGQTable.K1Luminance;
        } else {
            Table = JPEGQTable.K2Chrominance;
        }
        // Making sure the compression level is between 1 and 99
        compressionLevel = Math.max(1, Math.min(99, compressionLevel));

        // Based on a rule
        float scaleFactor;
        if (compressionLevel < 50) {
            scaleFactor = (float) (50.0 / compressionLevel);
        } else {
            scaleFactor = (float) (2 - (compressionLevel / 50.0));
        }
        return Table.getScaledInstance(scaleFactor, true).getTable();
    }

    private double[][] extractBlocks(double[][] input, int i, int j) {
        // Now we have to extract one block from this position
        double[][] block = new double[BLOCK_SIZE][BLOCK_SIZE];

        for (int x = 0; x < BLOCK_SIZE; x++) {
            for (int y = 0; y < BLOCK_SIZE; y++) {
                //Get the correct element of an array and prepare it for DCT

                // If we are out of bounds, add a zero there
                block[x][y] = (i + x < input.length || j + y < input[0].length) ? input[i + x][j + y] - 128 : 0;
            }
        }
        return block;

    }


    private double[][] performDCT(double[][] block) {

        // c1 is used when q or p is 0
        double c1 = Math.sqrt((double) 1 / BLOCK_SIZE);
        // Otherwise we use c2
        double c2 = Math.sqrt((double) 2 / BLOCK_SIZE);

        double[][] temp_block = new double[BLOCK_SIZE][BLOCK_SIZE];

        for (int p = 0; p < BLOCK_SIZE; p++) {
            for (int q = 0; q < BLOCK_SIZE; q++) {

                double sum = computeDCTCoefficient(block, p, q);
                double alfaP = (p == 0) ? c1 : c2;
                double alfaQ = (q == 0) ? c1 : c2;
                temp_block[p][q] = sum * alfaP * alfaQ;
            }
        }
        return temp_block;
    }

    private double computeDCTCoefficient(double[][] block, int p, int q) {

        double sum = 0;
        //Not BLOCK_SIZE -1, as that should end at 6
        for (int m = 0; m < BLOCK_SIZE; m++) {
            for (int n = 0; n < BLOCK_SIZE; n++) {
                sum += block[m][n] * COSINES[m][p] * COSINES[n][q];
            }
        }
        return sum;
    }

    public static void main(String[] args) {
        launch(args);
    }
}