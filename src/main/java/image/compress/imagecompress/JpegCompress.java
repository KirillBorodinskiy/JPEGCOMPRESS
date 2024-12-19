package image.compress.imagecompress;

import javafx.util.Pair;

import javax.imageio.plugins.jpeg.JPEGQTable;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static image.compress.imagecompress.ImageCompressionApp.*;

public class JpegCompress {
    public static final int BLOCK_SIZE = 8;
    public static final String LUMINANCE = "Luminance";
    public static final String CHROMINANCE = "Chrominance";
    public static final double[][] COSINES = precomputeCosines();

    private static double[][] precomputeCosines() {
        double[][] cosines = new double[BLOCK_SIZE][BLOCK_SIZE];

        for (int m = 0; m < BLOCK_SIZE; m++) {
            for (int p = 0; p < BLOCK_SIZE; p++) {
                cosines[m][p] = Math.cos(((2 * m + 1) * p * Math.PI) / (2 * BLOCK_SIZE));
            }
        }
        return cosines;
    }

    /**
     * Compresses the image using the following steps:
     * <ol>
     *   <li>Convert RGB to YCrCb.</li>
     *   <li>Crominance downsampling.</li>
     *   <li>Apply Discrete Cosine Transform (DCT) to image blocks.</li>
     *   <li>Quantize the DCT coefficients to reduce file size.</li>
     *   <li>Perform Run Length Encoding (RLE) and Huffman Encoding for further compression.</li>
     * </ol>
     *
     * @param compressionLevel the compression level (ranges from 1 to 99)
     */
    void compressImage(BufferedImage input, int compressionLevel) {
        int width = input.getWidth();
        int height = input.getHeight();
        double[][] luminanceValues = new double[height][width];
        double[][] chrominanceBlueValues = new double[height][width];
        double[][] chrominanceRedValues = new double[height][width];

        // Step 1: Convert RGB to YCrCb
        // The result will be inside the arrays
        convertToYCbCr(input, luminanceValues, chrominanceBlueValues, chrominanceRedValues);

        // Step 2: Crominance downsapling
        //THE SIZE OF AN ARRAY IS 4 TIMES SMALLER
        double[][] downsapledBlueValues = downsampleColorArray(chrominanceBlueValues);
        double[][] downsampledRedValues = downsampleColorArray(chrominanceRedValues);

        // Step 3,4: DCT, Quantization
        applyDCTAndQuantize(luminanceValues, LUMINANCE, compressionLevel);
        applyDCTAndQuantize(downsapledBlueValues, CHROMINANCE, compressionLevel);
        applyDCTAndQuantize(downsampledRedValues, CHROMINANCE, compressionLevel);
    }

    /**
     * Converts the RGB color values of the input image to the YCbCr color space.
     * The Y component represents brightness, while Cb and Cr components
     * represent color information. The computed Y, Cb, and Cr values
     * are stored in the provided 2D arrays.
     *
     * @param input   the input {@link BufferedImage} to be converted
     * @param YValue  a 2D array where the computed Y (luma) values will be stored
     * @param CbValue a 2D array where the computed Cb (blue-difference chroma) values will be stored
     * @param CrValue a 2D array where the computed Cr (red-difference chroma) values will be stored
     */
    private void convertToYCbCr(BufferedImage input, double[][] YValue, double[][] CbValue, double[][] CrValue) {
        int width = input.getWidth();
        int height = input.getHeight();

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
        LOGGER.info("Converting to YCbCr finished");
    }

    /**
     * Downsamples a 2D array by averaging 2x2 blocks of elements. The resulting
     * array has half the height and half the width of the original array.
     * Elements on the edge of the array that do not form a full 2x2 block,
     * are treated  as 0.
     *
     * @param Color the input 2D array representing color channel data
     * @return a new 2D array that is downsampled by averaging 2x2 blocks of the input array
     */
    private double[][] downsampleColorArray(double[][] Color) {
        int height = Color.length;
        int width = Color[0].length;

        int newHeight = height / 2;
        int newWidth = width / 2;

        double[][] downsampledColor = new double[newHeight][newWidth];

        for (int y = 0; y < height; y += 2) {
            for (int x = 0; x < width; x += 2) {

                //This ensures that we are not out of bounds
                double firstElement = Color[y][x];//This one is always safe
                double secondElement = getSafeValueAt(Color, y, x + 1);
                double thirdElement = getSafeValueAt(Color, y + 1, x);
                double fourthElement = getSafeValueAt(Color, y + 1, x + 1);


                downsampledColor[y / 2][x / 2] = (firstElement + secondElement + thirdElement + fourthElement) / 4;
            }
        }
        LOGGER.info("Downsampling color finished");
        return downsampledColor;
    }

    /**
     * Applies the Discrete Cosine Transform (DCT) to the input 2D array in 8x8 blocks.
     * Each block is transformed using the DCT, followed by quantization based on
     * the provided type and compression level. This process is commonly used in
     * image compression to reduce redundancy in image data.
     *
     * @param input            the 2D array representing the image or color channel to be processed
     * @param type             the quantization type to be applied; must be either {@code LUMINANCE} or {@code CHROMINANCE}
     * @param compressionLevel the level of compression, ranging from 1 (maximum compression)
     *                         to 99 (minimum compression)
     */
    private void applyDCTAndQuantize(double[][] input, String type, int compressionLevel) {
        // Formula from
        // https://www.mathworks.com/help/images/discrete-cosine-transform.html

        int inputHeight = input.length;
        int inputWidth = input[0].length;

        //Blocks of 8x8
        for (int i = 0; i < inputHeight; i += BLOCK_SIZE) {// HEIGHT
            for (int j = 0; j < inputWidth; j += BLOCK_SIZE) {// WIDTH

                double[][] block = extractBlocks(input, i, j);
                double[][] dctBlock = performDCT(block);


                // Step 4: Quantization
                int[][] quantizedDctBlock = performQuantization(dctBlock, type, compressionLevel);
                int[] zigZagBlock = convertToZigZag(quantizedDctBlock);
                List<Pair<Integer, Integer>> rleBlock = runLengthEncode(zigZagBlock);
                huffman(rleBlock);
            }
        }
        LOGGER.info("DCT and quantization finished");
    }

    private void huffman(List<Pair<Integer, Integer>> rleBlock) {
    }


    /**
     * Converts a 2D array of quantized DCT coefficients into a 1D array using
     * the zig-zag scanning order.
     *
     * @param quantizedDctBlock a 2D array of quantized DCT coefficients
     * @return a 1D array containing the reordered coefficients in zig-zag scanning order
     */
    private int[] convertToZigZag(int[][] quantizedDctBlock) {

        int rows = quantizedDctBlock.length;
        int columns = quantizedDctBlock[0].length;

        int[] zigZagBlock = new int[rows * columns];

        int[] zigZagOrder = {
                0, 1, 5, 6, 14, 15, 27, 28,
                2, 4, 7, 13, 16, 26, 29, 42,
                3, 8, 12, 17, 25, 30, 41, 43,
                9, 11, 18, 24, 31, 40, 44, 53,
                10, 19, 23, 32, 39, 45, 52, 54,
                20, 22, 33, 38, 46, 51, 55, 60,
                21, 34, 37, 47, 50, 56, 59, 61,
                35, 36, 48, 49, 57, 58, 62, 63
        };

        for (int i = 0; i < rows * columns; i++) {
            int zigZagIndex = zigZagOrder[i];
            int row = zigZagIndex / columns;
            int col = zigZagIndex % columns;
            zigZagBlock[i] = quantizedDctBlock[row][col];
        }
        LOGGER.info("Converting to zig-zag finished");
        return zigZagBlock;
    }

    /**
     * Performs Run-Length Encoding (RLE) on the input zig-zag scanned block.
     * The method compresses consecutive repeating values into a pair of the value and its run length.
     * An additional pair (0,0) is appended at the end of the output as per JPEG RLE compression rules.
     *
     * @param zigZagBlock an array of integers representing the zig-zag scanned block
     * @return a list of pairs, where each pair consists of an integer value from
     * the input array and its corresponding run length.
     */
    private List<Pair<Integer, Integer>> runLengthEncode(int[] zigZagBlock) {
        List<Pair<Integer, Integer>> encodedBlock = new ArrayList<>();

        for (int index = 0; index < zigZagBlock.length; index++) {

            //Each element is seen at least once
            int count = 1;
            while (index < zigZagBlock.length - 1 && zigZagBlock[index] == zigZagBlock[index + 1]) {
                count++;
                index++;

            }
            encodedBlock.add(new Pair<>(zigZagBlock[index], count));
        }
        // This is added based on JPEG RLE compression rules
        encodedBlock.add(new Pair<>(0, 0));
        LOGGER.info("RLE finished");
        return encodedBlock;
    }

    /**
     * Performs quantization on an 8x8 block of values from the DCT.
     * Each value in the block is divided by a corresponding quantization value and rounded
     * to the nearest integer.
     *
     * @param block            the 8x8 block of DCT-transformed values to be quantized
     * @param type             the quantization type; must be either {@code LUMINANCE} or {@code CHROMINANCE}
     * @param compressionLevel the compression level (1-99)
     * @return a quantized 8x8 block of values with reduced precision for compression
     */
    private int[][] performQuantization(double[][] block, String type, float compressionLevel) {

        int[][] quantizedBlock = new int[BLOCK_SIZE][BLOCK_SIZE];

        int[] quantizationValues = calculateQuantizationTable(type, compressionLevel);

        for (int i = 0; i < BLOCK_SIZE; i++) {
            for (int j = 0; j < BLOCK_SIZE; j++) {
                //round it to the nearest int
                quantizedBlock[i][j] = (int) block[i][j] / quantizationValues[i * BLOCK_SIZE + j];
            }
        }
        LOGGER.info("Quantization finished");
        return quantizedBlock;
    }

    /**
     * Calculates the quantization table for a specified type (luminance or chrominance)
     * and compression level. The table is scaled based on the compression level to
     * adjust the balance between quality and compression ratio.
     *
     * @param type             the quantization type; must be either {@code LUMINANCE} or {@code CHROMINANCE}
     * @param compressionLevel the compression level between 1 and 99,
     *                         where 1 represents the highest compression and 99 represents the lowest compression
     * @return an array representing the scaled quantization table values for an 8x8 block
     */
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
        LOGGER.info("Scale factor: " + scaleFactor);
        return Table.getScaledInstance(scaleFactor, true).getTable();
    }

    /**
     * Extracts an 8x8 block of values from the specified position in the input 2D array.
     * The block is prepared for the DCT by subtracting 128 from each pixel value to center the data around zero.
     * If the specified block extends beyond the bounds of the input array,
     * out-of-bounds positions are filled with zero.
     *
     * @param input the input 2D array representing the image or data to be processed
     * @param i     the starting row index of the block within the input array
     * @param j     the starting column index of the block within the input array
     * @return an 8x8 block of centered values extracted from the input array, with out-of-bounds positions filled with zeros
     */
    private double[][] extractBlocks(double[][] input, int i, int j) {
        // Now we have to extract one block from this position
        double[][] block = new double[BLOCK_SIZE][BLOCK_SIZE];

        for (int x = 0; x < BLOCK_SIZE; x++) {
            for (int y = 0; y < BLOCK_SIZE; y++) {

                //Get the correct element of an array and prepare it for DCT
                block[x][y] = getSafeValueAt(input, i + x, j + y) - 128;
            }
        }
        return block;

    }

    /**
     * Performs the Discrete Cosine Transform (DCT) on an 8x8 block of values.
     *
     * @param block the 8x8 block of spatial data (typically pixel values) to be transformed
     * @return an 8x8 block of frequency-domain coefficients resulting from the DCT
     */
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

    /**
     * Computes a single coefficient for DCT at the specified indices `p` and `q`.
     *
     * @param block the 8x8 block of pixel values to be transformed
     * @param p     the row index of the frequency in the DCT result
     * @param q     the column index of the frequency in the DCT result
     * @return the computed DCT coefficient for the specified  indices `p` and `q`
     */
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
}
