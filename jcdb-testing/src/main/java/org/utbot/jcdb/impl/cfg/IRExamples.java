package org.utbot.jcdb.impl.cfg;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class IRExamples {
    int x;

    public void testField(int x) {
        this.x = x;
    }

    public int testPrimitives(int a, int b) {
        int c = 0;
        if (a > b) {
            c = a;
        } else {
            c = b;
        }
        int d = c;
        int e = 0;
        if (d > 100) {
            e = 100 - d;
        } else {
            e = -d;
        }
        return e;
    }

    public int runBinarySearchIteratively(
            int[] sortedArray, int key, int low, int high) {
        int index = Integer.MAX_VALUE;

        while (low <= high) {
            int mid = low + ((high - low) / 2);
            if (sortedArray[mid] < key) {
                low = mid + 1;
            } else if (sortedArray[mid] > key) {
                high = mid - 1;
            } else if (sortedArray[mid] == key) {
                index = mid;
                break;
            }
        }
        return index;
    }

    public boolean test(int element) {
        return (element == 123);
    }

    static public void sortTimes(String inputName, String outputName) throws IOException {
        String hour;
        try (BufferedReader reader = new BufferedReader(new FileReader(inputName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                hour = line.substring(0, 2);
                if (Integer.parseInt(hour) >= 60) {
                    throw new NumberFormatException();
                }
            }
        }
    }

    static public void sortSequence(String inputName, String outputName) throws IOException {
        int[] types = new int[100000000]; // Массив, в котором индекс используется как вид считанного числа, а

        try (BufferedReader reader = new BufferedReader(new FileReader(inputName))) {
            String line;

            while ((line = reader.readLine()) != null) {
                int current = Integer.parseInt(line); // Считывание текущего числа
                types[current]++;  // Указание вида считанного числа в массиве types
            }
        } catch (IOException e) {
        }

        // Поиск наиболее встречаемого вида числа
        // O(n)
        for (int i = 0; i < types.length - 1; i++) {
        }
    }
}
