package org.utbot.jcdb.impl.cfg;

import kotlin.NotImplementedError;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class JavaTasks {
    /**
     * Сортировка времён
     * <p>
     * Простая
     * (Модифицированная задача с сайта acmp.ru)
     * <p>
     * Во входном файле с именем inputName содержатся моменты времени в формате ЧЧ:ММ:СС AM/PM,
     * каждый на отдельной строке. См. статью википедии "12-часовой формат времени".
     * <p>
     * Пример:
     * <p>
     * 01:15:19 PM
     * 07:26:57 AM
     * 10:00:03 AM
     * 07:56:14 PM
     * 01:15:19 PM
     * 12:40:31 AM
     * <p>
     * Отсортировать моменты времени по возрастанию и вывести их в выходной файл с именем outputName,
     * сохраняя формат ЧЧ:ММ:СС AM/PM. Одинаковые моменты времени выводить друг за другом. Пример:
     * <p>
     * 12:40:31 AM
     * 07:26:57 AM
     * 10:00:03 AM
     * 01:15:19 PM
     * 01:15:19 PM
     * 07:56:14 PM
     * <p>
     * В случае обнаружения неверного формата файла бросить любое исключение.
     */
    // Сортировка вставками (сортируется двумерный список по кол-ву секунд (time.get(i).get(3)))
    // T = O(n ^ 2)
    // R = O(1) - количество переменных не зависит от размера входа
    public static void insertionSort(List<List<String>> time) {
        for (int i = 1; i < time.size(); i++) {
            List<String> currentList = time.get(i);
            int current = Integer.parseInt(time.get(i).get(3));
            int j = i - 1;

            for (; j >= 0; j--) {
                if (Integer.parseInt(time.get(j).get(3)) > current) time.set(j + 1, time.get(j));
                else break;
            }

            time.set(j + 1, currentList);
        }
    }

    // Оценка сложности алгоритма: T = 2 * O(n ^ 2) + 2 * O(n) = O(n ^ 2)
    //                             R = O(1)
    static public void sortTimes(String inputName, String outputName) throws IOException {
        List<List<String>> timeAM = new ArrayList<>(); // Двумерный список для записи времени при AM
        List<List<String>> timePM = new ArrayList<>(); // Двумерный список для записи времени при PM
        String hour, min, sec; // Переменные, используемые для записи моментов времени
        String timeFlag; // Переменна, в которую записывается AM или PM
        int sum; // Итоговое кол-во секунд, используемое для сортировки

        // O(n)
        try (BufferedReader reader = new BufferedReader(new FileReader(inputName))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Текущий список, используемый для дальнейшей записи данных в timeAM или timePM
                List<String> current = new ArrayList<>();

                hour = line.substring(0, 2);
                min = line.substring(3, 5);
                sec = line.substring(6, 8);
                timeFlag = line.substring(9, 11);

                // Проверка формата записи
                if (Integer.parseInt(hour) >= 60 || Integer.parseInt(min) >= 60 || Integer.parseInt(sec) >= 60) {
                    throw new NumberFormatException();
                }

                // Вычисление итогового кол-ва секунд ("12" считается за "00")
                if (hour.equals("12")) sum = Integer.parseInt(min) * 60 + Integer.parseInt(sec);
                else sum = Integer.parseInt(hour) * 3600 + Integer.parseInt(min) * 60 + Integer.parseInt(sec);

                // Добавление составляющих момента времени в список current
                current.add(hour);
                current.add(min);
                current.add(sec);
                current.add(String.valueOf(sum));
                // Добавление текущих данных (current) в список timeAM или timePM
                if (timeFlag.equals("AM")) timeAM.add(current);
                else if (timeFlag.equals("PM")) timePM.add(current);
                else throw new NumberFormatException();
            }
        }

        // Сортировка вставками по итоговому кол-ву секунд обоих списков
        // 2 * O(n ^ 2)
        insertionSort(timeAM);
        insertionSort(timePM);

        // O(n)
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputName))) {
            for (List<String> currentList : timeAM) {
                writer.write(currentList.get(0) + ":" + currentList.get(1) + ":" + currentList.get(2) + " AM\n");
            }
            for (List<String> currentList : timePM) {
                writer.write(currentList.get(0) + ":" + currentList.get(1) + ":" + currentList.get(2) + " PM\n");
            }
        }
    }

    /**
     * Сортировка адресов
     * <p>
     * Средняя
     * <p>
     * Во входном файле с именем inputName содержатся фамилии и имена жителей города с указанием улицы и номера дома,
     * где они прописаны. Пример:
     * <p>
     * Петров Иван - Железнодорожная 3
     * Сидоров Петр - Садовая 5
     * Иванов Алексей - Железнодорожная 7
     * Сидорова Мария - Садовая 5
     * Иванов Михаил - Железнодорожная 7
     * <p>
     * Людей в городе может быть до миллиона.
     * <p>
     * Вывести записи в выходной файл outputName,
     * упорядоченными по названию улицы (по алфавиту) и номеру дома (по возрастанию).
     * Людей, живущих в одном доме, выводить через запятую по алфавиту (вначале по фамилии, потом по имени). Пример:
     * <p>
     * Железнодорожная 3 - Петров Иван
     * Железнодорожная 7 - Иванов Алексей, Иванов Михаил
     * Садовая 5 - Сидоров Петр, Сидорова Мария
     * <p>
     * В случае обнаружения неверного формата файла бросить любое исключение.
     */
    static public void sortAddresses(String inputName, String outputName) {
        throw new NotImplementedError();
    }

    /**
     * Сортировка температур
     * <p>
     * Средняя
     * (Модифицированная задача с сайта acmp.ru)
     * <p>
     * Во входном файле заданы температуры различных участков абстрактной планеты с точностью до десятых градуса.
     * Температуры могут изменяться в диапазоне от -273.0 до +500.0.
     * Например:
     * <p>
     * 24.7
     * -12.6
     * 121.3
     * -98.4
     * 99.5
     * -12.6
     * 11.0
     * <p>
     * Количество строк в файле может достигать ста миллионов.
     * Вывести строки в выходной файл, отсортировав их по возрастанию температуры.
     * Повторяющиеся строки сохранить. Например:
     * <p>
     * -98.4
     * -12.6
     * -12.6
     * 11.0
     * 24.7
     * 99.5
     * 121.3
     */
    // Пирамидальная сортировка
    // T = O(n * lon n)
    // R = O(1) - сортирует на месте
    public static void heapSort(List<Double> arr) {
        int n = arr.size();

        // Построение кучи (перегруппировка массива)
        for (int i = n / 2 - 1; i >= 0; i--) {
            heapify(arr, n, i);
        }

        // Поочерёдное извлечение элементов из кучи
        for (int i = n - 1; i >= 0; i--) {
            // Перемещение текущего корня в конец
            double temp = arr.get(0);
            arr.set(0, arr.get(i));
            arr.set(i, temp);

            // Вызов функции heapify на уменьшенной куче
            heapify(arr, i, 0);
        }
    }

    // Функция преобразования в двоичную кучу поддерева с корневым узлом i
    // (i - индекс в списке, n - размер кучи)
    public static void heapify(List<Double> arr, int n, int i) {
        int largest = i; // Наибольший элемент - корень
        int l = 2 * i + 1; // Левый элемент
        int r = 2 * i + 2; // Правый элемент

        // Случай, при котором левый потомок больше корня
        if (l < n && arr.get(l) > arr.get(largest)) largest = l;

        // Случай, при котором правый потомок больше корня
        if (r < n && arr.get(r) > arr.get(largest)) largest = r;

        if (largest != i) {
            double temp = arr.get(i);
            arr.set(i, arr.get(largest));
            arr.set(largest, temp);

            // Рекурсивное преобразование в двоичную кучу затронутое поддерево
            heapify(arr, n, largest);
        }
    }

    // Оценка сложности алгоритма: T = 2 * O(n) + O(n * log n) = O(n * log n);
    //                             R = O(n)
    static public void sortTemperatures(String inputName, String outputName) {
        List<Double> temperature = new ArrayList<>(); // Список, используемый для записи температур

        // O(n)
        try (BufferedReader reader = new BufferedReader(new FileReader(inputName))) {
            String line;

            while ((line = reader.readLine()) != null) {
                // Запись текущей температуры в список temperature
                double number = Double.parseDouble(line);
                temperature.add(number);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Пирамидальная сортировка полученного списка
        // O(n * log n)
        // R(1)
        heapSort(temperature);


        // O(n)
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputName))) {
            for (Double number : temperature) {
                writer.write((number) + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Сортировка последовательности
     * <p>
     * Средняя
     * (Задача взята с сайта acmp.ru)
     * <p>
     * В файле задана последовательность из n целых положительных чисел, каждое в своей строке, например:
     * <p>
     * 1
     * 2
     * 3
     * 2
     * 3
     * 1
     * 2
     * <p>
     * Необходимо найти число, которое встречается в этой последовательности наибольшее количество раз,
     * а если таких чисел несколько, то найти минимальное из них,
     * и после этого переместить все такие числа в конец заданной последовательности.
     * Порядок расположения остальных чисел должен остаться без изменения.
     * <p>
     * 1
     * 3
     * 3
     * 1
     * 2
     * 2
     * 2
     */
    // Оценка сложности алгоритма:
    // T = 4 * O(n) = O(n)
    // R = O(1) - количество переменных не зависит от размера входа
    static public void sortSequence(String inputName, String outputName) {
        int[] types = new int[100000000]; // Массив, в котором индекс используется как вид считанного числа, а
        // значение - кол-во чисел определенного вида
        ArrayList<Integer> numbers = new ArrayList<>(); // список, используемый для записи всех чисел из исходного файла
        int maxNumberType = -1; // Вид числа, который встречается чаще всех
        int maxNumberValue = -1; // Наибольшее кол-во чисел определенного вида

        // O(n)
        try (BufferedReader reader = new BufferedReader(new FileReader(inputName))) {
            String line;

            while ((line = reader.readLine()) != null) {
                int current = Integer.parseInt(line); // Считывание текущего числа
                numbers.add(current);  // Запись числа в общий список
                types[current]++;  // Указание вида считанного числа в массиве types
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Поиск наиболее встречаемого вида числа
        // O(n)
        for (int i = 0; i < types.length - 1; i++) {
            if (types[i] > maxNumberValue) {
                if (maxNumberType < i) maxNumberType = i; // вид maxNumberType должен быть минимальным по условию
                else continue;
                maxNumberValue = types[i];
            }
        }

        // Удаление чисел вида maxNumberType из списка numbers
        // O(n)
        int finalMaxNumberType = maxNumberType;
        numbers.removeIf(element -> (element == finalMaxNumberType));

        // o(n)
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputName))) {
            // Запись основного списка чисел в файл
            for (Integer number : numbers) {
                writer.write(number + "\n");
            }

            // Запись наиболее встречаемого вида чисел maxNumberValue раз
            for (int i = 0; i < maxNumberValue; i++) {
                writer.write(maxNumberType + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Соединить два отсортированных массива в один
     * <p>
     * Простая
     * <p>
     * Задан отсортированный массив first и второй массив second,
     * первые first.size ячеек которого содержат null, а остальные ячейки также отсортированы.
     * Соединить оба массива в массиве second так, чтобы он оказался отсортирован. Пример:
     * <p>
     * first = [4 9 15 20 28]
     * second = [null null null null null 1 3 9 13 18 23]
     * <p>
     * Результат: second = [1 3 4 9 9 13 15 20 23 28]
     */
    static <T extends Comparable<T>> void mergeArrays(T[] first, T[] second) {
        throw new NotImplementedError();
    }

    static public String longestCommonSubstring(String first, String second) {
        if (first.isEmpty() || second.isEmpty()) {
            return "";
        }
        // Пусть twoDimArray[][] = A[][]
        int[][] twoDimArray = new int[first.length() + 1][second.length() + 1];
        int index = 0;   // индекс текущего символа в первой строке
        int maxReps = 0; // максимальное число повторений
        for (int i = 1; i <= first.length(); i++) {
            for (int j = 1; j <= second.length(); j++) {
                if (first.charAt(i - 1) != second.charAt(j - 1)) {
                    twoDimArray[i][j] = 0;
                }
                else {
                    twoDimArray[i][j] = twoDimArray[i - 1][j - 1] + 1;
                    if (twoDimArray[i][j] > maxReps) {
                        maxReps = twoDimArray[i][j];
                        index = i;
                    }
                }

            }
        }
        return first.substring(index - maxReps, index);
    }
    // n = first.length(); m = second.length();
    // Трудоёмкость (Время) - O(n * m)
    // Ресурсоёмкость (Память) - O(n * m)

    /**
     * Число простых чисел в интервале
     * Простая
     *
     * Рассчитать количество простых чисел в интервале от 1 до limit (включительно).
     * Если limit <= 1, вернуть результат 0.
     *
     * Справка: простым считается число, которое делится нацело только на 1 и на себя.
     * Единица простым числом не считается.
     */
    static public int calcPrimesNumber(int limit) { // Решето Эратосфена
        if (limit <= 1) return 0;
        int p = 2;
        int step = p;
        int numberPrimes = 0;
        boolean[] primeArray = new boolean[limit + 1]; // limit + 1, т.к. limit включительно
        while (p * p <= limit) {
            if (!primeArray[p]) {
                for (int cycleP = p * p; cycleP <= limit; cycleP += step) {
                    primeArray[cycleP] = true;
                }
            }
            p++;
            step = 2 * p;
        }
        for (int i = 2; i <= limit; i++) {
            if (!primeArray[i]) {
                numberPrimes++;
            }
        }
        return numberPrimes;
    }
}

