package image.compress.imagecompress;

import javafx.util.*;

public class HuffmanCoding implements Comparable<HuffmanCoding> {
    Pair<Integer, Integer> data;
    int frequency;
    HuffmanCoding left;
    HuffmanCoding right;

    public HuffmanCoding(Pair<Integer, Integer> data, int frequency) {
        this.data = data;
        this.frequency = frequency;
        left = null;
        right = null;
    }

    @Override
    public int compareTo(HuffmanCoding otherNode) {
        return this.frequency - otherNode.frequency;
    }
}
