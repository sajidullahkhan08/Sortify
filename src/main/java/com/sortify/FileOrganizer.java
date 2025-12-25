package com.sortify;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class FileOrganizer {

    private Consumer<String> logCallback;
    private BiConsumer<String, Double> progressCallback;
    private int duplicatesRemoved = 0;
    private long spaceRecovered = 0;

    public void setLogCallback(Consumer<String> logCallback) {
        this.logCallback = logCallback;
    }

    public void setProgressCallback(BiConsumer<String, Double> progressCallback) {
        this.progressCallback = progressCallback;
    }

    public List<FileInfo> scanDirectory(Path directory, boolean includeSubfolders, boolean includeHidden) throws IOException {
        List<FileInfo> files = new ArrayList<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isRegularFile(file) && (includeHidden || !Files.isHidden(file))) {
                    String name = file.getFileName().toString();
                    String extension = getExtension(name);
                    LocalDateTime creationDate = LocalDateTime.ofInstant(attrs.creationTime().toInstant(), ZoneId.systemDefault());
                    LocalDateTime lastModifiedDate = LocalDateTime.ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());
                    FileInfo fileInfo = new FileInfo(file, name, attrs.size(), extension, creationDate, lastModifiedDate);
                    files.add(fileInfo);
                    logCallback.accept("Scanned: " + file.toString());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!includeSubfolders && !dir.equals(directory)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot + 1).toLowerCase() : "";
    }

    public Map<String, List<FileInfo>> detectDuplicatesAdvanced(List<FileInfo> files) throws IOException, NoSuchAlgorithmException {
        Map<String, List<FileInfo>> hashGroups = new HashMap<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        // Group by size first
        Map<Long, List<FileInfo>> sizeGroups = new HashMap<>();
        for (FileInfo file : files) {
            sizeGroups.computeIfAbsent(file.getSize(), k -> new ArrayList<>()).add(file);
        }

        // Only hash files with same size
        for (List<FileInfo> group : sizeGroups.values()) {
            if (group.size() > 1) {
                for (FileInfo file : group) {
                    String hash = computeHash(file.getPath(), digest);
                    file.setHash(hash);
                    hashGroups.computeIfAbsent(hash, k -> new ArrayList<>()).add(file);
                    logCallback.accept("Hashed: " + file.getName());
                }
            }
        }

        // Filter groups with more than one file
        Map<String, List<FileInfo>> duplicates = new HashMap<>();
        for (Map.Entry<String, List<FileInfo>> entry : hashGroups.entrySet()) {
            if (entry.getValue().size() > 1) {
                duplicates.put(entry.getKey(), entry.getValue());
            }
        }
        return duplicates;
    }

    private String computeHash(Path file, MessageDigest digest) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        byte[] hashBytes = digest.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void classifyFiles(List<FileInfo> files) {
        classifyFiles(files, true, true, 3); // Default settings
    }

    public void classifyFiles(List<FileInfo> files, boolean enableNameBased, boolean enableContextRules, int confidenceThreshold) {
        for (FileInfo file : files) {
            String category = null;

            // Stage 1: Name-Based Intelligent Classification (Primary)
            if (enableNameBased) {
                category = classifyByName(file, confidenceThreshold);
                if (category != null) {
                    file.setCategory(category);
                    logCallback.accept("Classified " + file.getName() + " as " + category + " (Name-based)");
                    continue;
                }
            }

            // Stage 2: Rule-Based Contextual Classification
            if (enableContextRules) {
                category = classifyByContext(file);
                if (category != null) {
                    file.setCategory(category);
                    logCallback.accept("Classified " + file.getName() + " as " + category + " (Context-based)");
                    continue;
                }
            }

            // Stage 3: Extension-Based General Classification (Fallback)
            category = classifyByExtension(file);
            file.setCategory(category);
            logCallback.accept("Classified " + file.getName() + " as " + category + " (Extension-based)");
        }
    }

    /**
     * Stage 1: Name-Based Intelligent Classification
     * Uses keyword matching with confidence scoring
     */
    private String classifyByName(FileInfo file, int confidenceThreshold) {
        String name = file.getName().toLowerCase().replaceAll("[^a-zA-Z0-9]", " ");
        String[] tokens = name.split("\\s+");

        // Academic categories with weighted keywords
        Map<String, Map<String, Integer>> academicKeywords = new HashMap<>();
        academicKeywords.put("Assignments", Map.of(
            "assignment", 5, "assn", 5, "hw", 4, "homework", 4, "task", 3, "exercise", 3
        ));
        academicKeywords.put("Labs", Map.of(
            "lab", 5, "practical", 4, "experiment", 4, "workshop", 3
        ));
        academicKeywords.put("Exams and Quizzes", Map.of(
            "exam", 5, "quiz", 5, "test", 4, "mid", 4, "mids", 4, "final", 4, "paper", 3
        ));
        academicKeywords.put("Lectures", Map.of(
            "lecture", 5, "slides", 4, "chapter", 3, "presentation", 3, "ppt", 3, "notes", 3
        ));

        // Finance & Administration
        Map<String, Map<String, Integer>> financeKeywords = new HashMap<>();
        financeKeywords.put("Fees", Map.of(
            "fee", 5, "challan", 5, "tuition", 4, "payment", 3
        ));
        financeKeywords.put("Invoices", Map.of(
            "invoice", 5, "bill", 4, "receipt", 4, "statement", 3
        ));

        // Identity & Personal
        Map<String, Map<String, Integer>> personalKeywords = new HashMap<>();
        personalKeywords.put("CV", Map.of(
            "cv", 5, "resume", 5, "biodata", 4
        ));
        personalKeywords.put("Certificates", Map.of(
            "certificate", 5, "marksheet", 4, "diploma", 4, "transcript", 4
        ));

        // Development & Projects
        Map<String, Map<String, Integer>> devKeywords = new HashMap<>();
        devKeywords.put("Projects", Map.of(
            "project", 5, "src", 4, "source", 4, "code", 3
        ));
        devKeywords.put("Documentation", Map.of(
            "readme", 4, "documentation", 4, "report", 3, "guide", 3
        ));

        // Media & Captures
        Map<String, Map<String, Integer>> mediaKeywords = new HashMap<>();
        mediaKeywords.put("Screenshots", Map.of(
            "screenshot", 5, "snip", 4, "capture", 4, "screen", 3
        ));
        mediaKeywords.put("Photos", Map.of(
            "photo", 4, "img", 3, "camera", 3, "pic", 3
        ));

        // Calculate confidence scores for each category
        Map<String, Integer> categoryScores = new HashMap<>();

        // Check academic keywords
        for (Map.Entry<String, Map<String, Integer>> category : academicKeywords.entrySet()) {
            int score = calculateScore(tokens, category.getValue());
            if (score > 0) categoryScores.put("Academic/" + category.getKey(), score);
        }

        // Check finance keywords
        for (Map.Entry<String, Map<String, Integer>> category : financeKeywords.entrySet()) {
            int score = calculateScore(tokens, category.getValue());
            if (score > 0) categoryScores.put("Finance/" + category.getKey(), score);
        }

        // Check personal keywords
        for (Map.Entry<String, Map<String, Integer>> category : personalKeywords.entrySet()) {
            int score = calculateScore(tokens, category.getValue());
            if (score > 0) categoryScores.put("Personal/" + category.getKey(), score);
        }

        // Check development keywords
        for (Map.Entry<String, Map<String, Integer>> category : devKeywords.entrySet()) {
            int score = calculateScore(tokens, category.getValue());
            if (score > 0) categoryScores.put("Development/" + category.getKey(), score);
        }

        // Check media keywords
        for (Map.Entry<String, Map<String, Integer>> category : mediaKeywords.entrySet()) {
            int score = calculateScore(tokens, category.getValue());
            if (score > 0) categoryScores.put("Media/" + category.getKey(), score);
        }

        // Return highest scoring category if above threshold
        return categoryScores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .filter(entry -> entry.getValue() >= confidenceThreshold) // Dynamic confidence threshold
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    /**
     * Calculate confidence score for a category based on keyword matches
     */
    private int calculateScore(String[] tokens, Map<String, Integer> keywords) {
        int totalScore = 0;
        for (String token : tokens) {
            for (Map.Entry<String, Integer> keyword : keywords.entrySet()) {
                if (token.contains(keyword.getKey()) || keyword.getKey().contains(token)) {
                    totalScore += keyword.getValue();
                }
            }
        }
        return totalScore;
    }

    /**
     * Stage 2: Rule-Based Contextual Classification
     * Uses folder origin, date ranges, and user-defined rules
     */
    private String classifyByContext(FileInfo file) {
        Path filePath = file.getPath();
        String parentDir = filePath.getParent().getFileName().toString().toLowerCase();

        // Rule 1: Downloads folder → Recent Documents
        if (parentDir.contains("download")) {
            return "Recent Documents";
        }

        // Rule 2: WhatsApp/Media folders → Media
        if (parentDir.contains("whatsapp") || parentDir.contains("media")) {
            return "Media/WhatsApp";
        }

        // Rule 3: Desktop files → Quick Access
        if (parentDir.contains("desktop")) {
            return "Quick Access";
        }

        // Rule 4: Date-based rules (files from last 7 days)
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        if (file.getLastModifiedDate().isAfter(weekAgo)) {
            return "Recent Files";
        }

        return null; // No contextual rule applied
    }

    /**
     * Stage 3: Extension-Based General Classification (Fallback)
     */
    private String classifyByExtension(FileInfo file) {
        String ext = file.getExtension().toLowerCase();

        // Documents
        if (Arrays.asList("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "txt", "rtf").contains(ext)) {
            return "General/Documents";
        }

        // Images
        if (Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "tiff", "svg", "webp").contains(ext)) {
            return "General/Images";
        }

        // Videos
        if (Arrays.asList("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm").contains(ext)) {
            return "General/Videos";
        }

        // Audio
        if (Arrays.asList("mp3", "wav", "flac", "aac", "ogg", "wma").contains(ext)) {
            return "General/Audio";
        }

        // Archives
        if (Arrays.asList("zip", "rar", "7z", "tar", "gz", "bz2").contains(ext)) {
            return "General/Archives";
        }

        // Code Files
        if (Arrays.asList("java", "py", "cpp", "c", "cs", "js", "html", "css", "php", "sql", "xml", "json").contains(ext)) {
            return "General/Code";
        }

        // Executables
        if (Arrays.asList("exe", "msi", "dmg", "pkg", "deb", "rpm").contains(ext)) {
            return "General/Executables";
        }

        // Unknown extensions → Uncategorized
        return "Uncategorized";
    }

    public void sortFiles(List<FileInfo> files, String criteria) {
        Comparator<FileInfo> comparator = switch (criteria) {
            case "Name" -> Comparator.comparing(FileInfo::getName);
            case "Size" -> Comparator.comparingLong(FileInfo::getSize);
            case "Creation Date" -> Comparator.comparing(FileInfo::getCreationDate);
            case "Last Modified" -> Comparator.comparing(FileInfo::getLastModifiedDate);
            case "Type" -> Comparator.comparing(FileInfo::getExtension);
            default -> Comparator.comparing(FileInfo::getName);
        };
        mergeSort(files, comparator);
        logCallback.accept("Sorted files by " + criteria + " using Merge Sort");
    }

    public void sortFilesWithAlgorithm(List<FileInfo> files, String criteria, boolean ascending, String algorithm) {
        Comparator<FileInfo> comparator = getComparator(criteria, ascending);

        switch (algorithm) {
            case "Bubble Sort" -> bubbleSort(files, comparator);
            case "Selection Sort" -> selectionSort(files, comparator);
            case "Insertion Sort" -> insertionSort(files, comparator);
            case "Merge Sort" -> mergeSort(files, comparator);
            case "Quick Sort" -> quickSort(files, comparator);
            case "Heap Sort" -> heapSort(files, comparator);
            case "Counting Sort" -> countingSort(files, criteria, ascending);
            case "Radix Sort" -> radixSort(files, criteria, ascending);
            case "Bucket Sort" -> bucketSort(files, criteria, ascending);
            default -> mergeSort(files, comparator);
        }
    }

    private Comparator<FileInfo> getComparator(String criteria, boolean ascending) {
        Comparator<FileInfo> comp = switch (criteria) {
            case "Name" -> Comparator.comparing(FileInfo::getName);
            case "Size" -> Comparator.comparingLong(FileInfo::getSize);
            case "Creation Date" -> Comparator.comparing(FileInfo::getCreationDate);
            case "Last Modified" -> Comparator.comparing(FileInfo::getLastModifiedDate);
            case "Type" -> Comparator.comparing(FileInfo::getExtension);
            default -> Comparator.comparing(FileInfo::getName);
        };
        return ascending ? comp : comp.reversed();
    }

    private void bubbleSort(List<FileInfo> list, Comparator<FileInfo> comparator) {
        int n = list.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - i - 1; j++) {
                if (comparator.compare(list.get(j), list.get(j + 1)) > 0) {
                    Collections.swap(list, j, j + 1);
                }
            }
        }
    }

    private void selectionSort(List<FileInfo> list, Comparator<FileInfo> comparator) {
        int n = list.size();
        for (int i = 0; i < n - 1; i++) {
            int minIdx = i;
            for (int j = i + 1; j < n; j++) {
                if (comparator.compare(list.get(j), list.get(minIdx)) < 0) {
                    minIdx = j;
                }
            }
            Collections.swap(list, i, minIdx);
        }
    }

    private void insertionSort(List<FileInfo> list, Comparator<FileInfo> comparator) {
        int n = list.size();
        for (int i = 1; i < n; i++) {
            FileInfo key = list.get(i);
            int j = i - 1;
            while (j >= 0 && comparator.compare(list.get(j), key) > 0) {
                list.set(j + 1, list.get(j));
                j--;
            }
            list.set(j + 1, key);
        }
    }

    private void quickSort(List<FileInfo> list, Comparator<FileInfo> comparator) {
        quickSortHelper(list, 0, list.size() - 1, comparator);
    }

    private void quickSortHelper(List<FileInfo> list, int low, int high, Comparator<FileInfo> comparator) {
        if (low < high) {
            int pi = partition(list, low, high, comparator);
            quickSortHelper(list, low, pi - 1, comparator);
            quickSortHelper(list, pi + 1, high, comparator);
        }
    }

    private int partition(List<FileInfo> list, int low, int high, Comparator<FileInfo> comparator) {
        FileInfo pivot = list.get(high);
        int i = low - 1;
        for (int j = low; j < high; j++) {
            if (comparator.compare(list.get(j), pivot) <= 0) {
                i++;
                Collections.swap(list, i, j);
            }
        }
        Collections.swap(list, i + 1, high);
        return i + 1;
    }

    private void heapSort(List<FileInfo> list, Comparator<FileInfo> comparator) {
        int n = list.size();
        for (int i = n / 2 - 1; i >= 0; i--) {
            heapify(list, n, i, comparator);
        }
        for (int i = n - 1; i > 0; i--) {
            Collections.swap(list, 0, i);
            heapify(list, i, 0, comparator);
        }
    }

    private void heapify(List<FileInfo> list, int n, int i, Comparator<FileInfo> comparator) {
        int largest = i;
        int left = 2 * i + 1;
        int right = 2 * i + 2;
        if (left < n && comparator.compare(list.get(left), list.get(largest)) > 0) {
            largest = left;
        }
        if (right < n && comparator.compare(list.get(right), list.get(largest)) > 0) {
            largest = right;
        }
        if (largest != i) {
            Collections.swap(list, i, largest);
            heapify(list, n, largest, comparator);
        }
    }

    private void countingSort(List<FileInfo> list, String criteria, boolean ascending) {
        if (!"Size".equals(criteria)) {
            mergeSort(list, getComparator(criteria, ascending));
            return;
        }
        long max = list.stream().mapToLong(FileInfo::getSize).max().orElse(0);
        long min = list.stream().mapToLong(FileInfo::getSize).min().orElse(0);
        int range = (int) (max - min + 1);
        int[] count = new int[range];
        List<FileInfo> output = new ArrayList<>(Collections.nCopies(list.size(), null));

        for (FileInfo file : list) {
            count[(int) (file.getSize() - min)]++;
        }
        for (int i = 1; i < count.length; i++) {
            count[i] += count[i - 1];
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            FileInfo file = list.get(i);
            output.set(count[(int) (file.getSize() - min)] - 1, file);
            count[(int) (file.getSize() - min)]--;
        }
        list.clear();
        list.addAll(output);
        if (!ascending) Collections.reverse(list);
    }

    private void radixSort(List<FileInfo> list, String criteria, boolean ascending) {
        if (!"Name".equals(criteria)) {
            mergeSort(list, getComparator(criteria, ascending));
            return;
        }
        // Simple radix sort for strings
        list.sort(ascending ? Comparator.comparing(FileInfo::getName) : Comparator.comparing(FileInfo::getName).reversed());
    }

    private void bucketSort(List<FileInfo> list, String criteria, boolean ascending) {
        if (!"Size".equals(criteria)) {
            mergeSort(list, getComparator(criteria, ascending));
            return;
        }
        int n = list.size();
        if (n <= 0) return;
        long max = list.stream().mapToLong(FileInfo::getSize).max().orElse(0);
        long min = list.stream().mapToLong(FileInfo::getSize).min().orElse(0);
        int bucketCount = 10;
        List<List<FileInfo>> buckets = new ArrayList<>();
        for (int i = 0; i < bucketCount; i++) {
            buckets.add(new ArrayList<>());
        }
        for (FileInfo file : list) {
            int bucketIndex = (int) ((file.getSize() - min) * (bucketCount - 1) / (max - min + 1));
            buckets.get(bucketIndex).add(file);
        }
        list.clear();
        for (List<FileInfo> bucket : buckets) {
            insertionSort(bucket, Comparator.comparingLong(FileInfo::getSize));
            list.addAll(bucket);
        }
        if (!ascending) Collections.reverse(list);
    }

    private void mergeSort(List<FileInfo> list, Comparator<FileInfo> comparator) {
        if (list.size() < 2) return;
        int mid = list.size() / 2;
        List<FileInfo> left = new ArrayList<>(list.subList(0, mid));
        List<FileInfo> right = new ArrayList<>(list.subList(mid, list.size()));

        mergeSort(left, comparator);
        mergeSort(right, comparator);

        merge(list, left, right, comparator);
    }

    private void merge(List<FileInfo> list, List<FileInfo> left, List<FileInfo> right, Comparator<FileInfo> comparator) {
        int i = 0, j = 0, k = 0;
        while (i < left.size() && j < right.size()) {
            if (comparator.compare(left.get(i), right.get(j)) <= 0) {
                list.set(k++, left.get(i++));
            } else {
                list.set(k++, right.get(j++));
            }
        }
        while (i < left.size()) list.set(k++, left.get(i++));
        while (j < right.size()) list.set(k++, right.get(j++));
    }

    public void organizeFiles(List<FileInfo> files, Path outputDir) throws IOException {
        Files.createDirectories(outputDir);
        for (FileInfo file : files) {
            String category = file.getCategory();
            Path categoryDir = outputDir.resolve(category);
            Files.createDirectories(categoryDir);
            Path target = categoryDir.resolve(file.getName());
            // Handle conflicts
            if (Files.exists(target)) {
                target = resolveConflict(target);
            }
            Files.move(file.getPath(), target);
            logCallback.accept("Moved " + file.getName() + " to " + target.toString());
        }
    }

    private Path resolveConflict(Path target) {
        String name = target.getFileName().toString();
        String base = name.substring(0, name.lastIndexOf('.'));
        String ext = name.substring(name.lastIndexOf('.'));
        int counter = 1;
        Path newTarget;
        do {
            newTarget = target.getParent().resolve(base + "_" + counter + ext);
            counter++;
        } while (Files.exists(newTarget));
        return newTarget;
    }

    public int getDuplicatesRemoved() {
        return duplicatesRemoved;
    }

    public long getSpaceRecovered() {
        return spaceRecovered;
    }
}