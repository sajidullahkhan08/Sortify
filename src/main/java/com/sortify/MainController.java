package com.sortify;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class MainController {

    @FXML private TabPane mainTabPane;
    @FXML private VBox progressPreviewCard;

    @FXML private TextField directoryField;
    @FXML private Button browseButton;
    @FXML private CheckBox includeSubfoldersCheck;
    @FXML private CheckBox includeHiddenCheck;
    @FXML private CheckBox duplicateDetectionCheck;
    @FXML private CheckBox autoClassificationCheck;
    @FXML private CheckBox sortingCheck;
    @FXML private CheckBox customRulesCheck;
    @FXML private CheckBox enableNameBasedCheck;
    @FXML private CheckBox enableContextRulesCheck;
    @FXML private Slider confidenceThresholdSlider;
    @FXML private Label confidenceThresholdLabel;
    @FXML private ComboBox<String> sortCriteriaCombo;
    @FXML private ComboBox<String> conflictResolutionCombo;
    @FXML private Button startButton;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private TextArea logArea;
    @FXML private VBox previewVBox;
    @FXML private ListView<String> categoryListView;
    @FXML private TreeView<String> directoryTreeView;
    @FXML private Button confirmButton;
    @FXML private Button cancelButton;
    @FXML private Label filesProcessedLabel;
    @FXML private Label duplicatesRemovedLabel;
    @FXML private Label spaceRecoveredLabel;
    @FXML private Label timeTakenLabel;
    @FXML private Button exportReportButton;
    @FXML private VBox searchSortVBox;
    @FXML private ComboBox<String> scopeCombo;
    @FXML private Button loadDataButton;
    @FXML private ComboBox<String> sortCriteriaCombo2;
    @FXML private ComboBox<String> sortOrderCombo;
    @FXML private ComboBox<String> sortAlgorithmCombo;
    @FXML private Button sortButton;
    @FXML private ComboBox<String> filterTypeCombo;
    @FXML private TextField minSizeField;
    @FXML private TextField maxSizeField;
    @FXML private Button filterButton;
    @FXML private TextField searchField;
    @FXML private Button searchButton;
    @FXML private TableView<FileInfo> fileTable;
    @FXML private Label analyticsLabel;
    @FXML private VBox duplicateVBox;
    @FXML private ComboBox<String> duplicateScopeCombo;
    @FXML private Button scanDuplicatesButton;
    @FXML private ListView<String> duplicateGroupsListView;
    @FXML private Button viewGroupButton;
    @FXML private Button recoverButton;
    @FXML private TableView<FileInfo> duplicateTable;
    @FXML private Label duplicateAnalyticsLabel;

    private Path selectedDirectory;
    private FileOrganizer organizer;
    private List<FileInfo> scannedFiles;
    private Map<String, List<FileInfo>> detectedDuplicates;
    private List<FileInfo> loadedFiles;
    private Map<String, List<FileInfo>> duplicateGroups;

    @FXML
    public void initialize() {
        organizer = new FileOrganizer();
        organizer.setProgressCallback(this::updateProgress);
        organizer.setLogCallback(this::logMessage);
        sortCriteriaCombo.getItems().addAll("Name", "Size", "Creation Date", "Last Modified", "Type");
        conflictResolutionCombo.getItems().addAll("Rename", "Skip", "Overwrite");
        scopeCombo.getItems().addAll("Entire Workspace", "Assignments", "Lectures", "Lab Work", "Projects", "Exams and Quizzes", "Code Files", "Reading Material", "Media", "Miscellaneous");
        sortCriteriaCombo2.getItems().addAll("Name", "Size", "Creation Date", "Last Modified", "Type");
        sortAlgorithmCombo.getItems().addAll("Bubble Sort", "Selection Sort", "Insertion Sort", "Merge Sort", "Quick Sort", "Heap Sort", "Counting Sort", "Radix Sort", "Bucket Sort");
        filterTypeCombo.getItems().addAll("PDF", "DOCX", "PPTX", "JAVA", "PY", "CPP", "JPG", "PNG", "MP4", "All");
        duplicateScopeCombo.getItems().addAll("Entire Workspace", "Assignments", "Lectures", "Lab Work", "Projects", "Exams and Quizzes", "Code Files", "Reading Material", "Media", "Miscellaneous");

        // Initialize confidence threshold slider listener
        confidenceThresholdSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            confidenceThresholdLabel.setText(String.valueOf(newVal.intValue()));
        });
    }

    @FXML
    private void browseDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Directory to Organize");
        File selectedDir = directoryChooser.showDialog(null);
        if (selectedDir != null) {
            selectedDirectory = selectedDir.toPath();
            directoryField.setText(selectedDirectory.toString());
        }
    }

    @FXML
    private void startOrganizing() {
        if (selectedDirectory == null) {
            showAlert("Error", "Please select a directory first.");
            return;
        }

        // Disable UI during processing
        setUIEnabled(false);

        // Run in background thread
        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();

                // Scan directory
                updateProgress("Scanning directory...", 0.1);
                List<FileInfo> files = organizer.scanDirectory(selectedDirectory, includeSubfoldersCheck.isSelected(), includeHiddenCheck.isSelected());

                // Detect duplicates if enabled
                Map<String, List<FileInfo>> duplicates = null;
                if (duplicateDetectionCheck.isSelected()) {
                    updateProgress("Detecting duplicates...", 0.3);
                    duplicates = organizer.detectDuplicatesAdvanced(files);
                }

                // Classify files if enabled
                if (autoClassificationCheck.isSelected()) {
                    updateProgress("Classifying files...", 0.5);
                    boolean enableNameBased = enableNameBasedCheck.isSelected();
                    boolean enableContextRules = enableContextRulesCheck.isSelected();
                    int confidenceThreshold = (int) confidenceThresholdSlider.getValue();
                    organizer.classifyFiles(files, enableNameBased, enableContextRules, confidenceThreshold);
                }

                // Sort files if enabled
                if (sortingCheck.isSelected()) {
                    updateProgress("Sorting files...", 0.7);
                    organizer.sortFiles(files, sortCriteriaCombo.getValue());
                }

                scannedFiles = files;
                detectedDuplicates = duplicates;

                Platform.runLater(() -> {
                    displayPreview(scannedFiles, detectedDuplicates);
                    setUIEnabled(true);
                    updateProgress("Preview ready. Review and confirm.", 1.0);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert("Error", "An error occurred: " + e.getMessage());
                    setUIEnabled(true);
                });
            }
        }).start();
    }

    private void updateProgress(String message, double progress) {
        Platform.runLater(() -> {
            progressLabel.setText(message);
            progressBar.setProgress(progress);
        });
    }

    private void logMessage(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    private void displayPreview(List<FileInfo> files, Map<String, List<FileInfo>> duplicates) {
        // Display categorization
        Map<String, Long> categoryCounts = files.stream()
            .collect(Collectors.groupingBy(FileInfo::getCategory, Collectors.counting()));
        ObservableList<String> categoryItems = FXCollections.observableArrayList();
        categoryCounts.forEach((cat, count) -> categoryItems.add(cat + ": " + count + " files"));
        categoryListView.setItems(categoryItems);

        // Display directory structure
        TreeItem<String> rootItem = new TreeItem<>("Sortify_Organized");
        rootItem.setExpanded(true);
        Map<String, TreeItem<String>> categoryItemsMap = new HashMap<>();
        for (FileInfo file : files) {
            String cat = file.getCategory();
            categoryItemsMap.computeIfAbsent(cat, k -> {
                TreeItem<String> catItem = new TreeItem<>(cat);
                rootItem.getChildren().add(catItem);
                return catItem;
            }).getChildren().add(new TreeItem<>(file.getName()));
        }
        directoryTreeView.setRoot(rootItem);

        // Note: Duplicates are now handled in the Duplicates tab separately

        previewVBox.setVisible(true);
        progressPreviewCard.setVisible(true);
    }

    private void updateSummary(int filesProcessed, int duplicatesRemoved, long spaceRecovered, double timeTaken) {
        filesProcessedLabel.setText(String.valueOf(filesProcessed));
        duplicatesRemovedLabel.setText(String.valueOf(duplicatesRemoved));
        spaceRecoveredLabel.setText(String.format("%.2f MB", spaceRecovered / (1024.0 * 1024.0)));
        timeTakenLabel.setText(String.format("%.2f s", timeTaken));
    }

    private void setUIEnabled(boolean enabled) {
        browseButton.setDisable(!enabled);
        startButton.setDisable(!enabled);
        duplicateDetectionCheck.setDisable(!enabled);
        autoClassificationCheck.setDisable(!enabled);
        sortingCheck.setDisable(!enabled);
        customRulesCheck.setDisable(!enabled);
        sortCriteriaCombo.setDisable(!enabled);
        conflictResolutionCombo.setDisable(!enabled);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    public void confirmAndOrganize() {
        if (scannedFiles == null) {
            showAlert("Error", "No files to organize.");
            return;
        }

        setUIEnabled(false);
        previewVBox.setVisible(false);
        progressPreviewCard.setVisible(false);

        new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();

                updateProgress("Organizing files...", 0.5);
                organizer.organizeFiles(scannedFiles, selectedDirectory.resolve("Sortify_Organized"));

                long endTime = System.currentTimeMillis();
                double timeTaken = (endTime - startTime) / 1000.0;

                int fileCount = scannedFiles.size();
                int dupRemoved = detectedDuplicates != null ? organizer.getDuplicatesRemoved() : 0;
                long spaceRecovered = organizer.getSpaceRecovered();

                Platform.runLater(() -> {
                    updateSummary(fileCount, dupRemoved, spaceRecovered, timeTaken);
                    setUIEnabled(true);
                    updateProgress("Completed!", 1.0);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert("Error", "An error occurred: " + e.getMessage());
                    setUIEnabled(true);
                });
            }
        }).start();
    }

    @FXML
    public void loadData() {
        if (scannedFiles == null || scannedFiles.isEmpty()) {
            showAlert("Error", "No files available. Please organize files first.");
            return;
        }

        String scope = scopeCombo.getValue();
        loadedFiles = new ArrayList<>();
        if ("Entire Workspace".equals(scope)) {
            loadedFiles.addAll(scannedFiles);
        } else {
            for (FileInfo file : scannedFiles) {
                if (scope.equals(file.getCategory())) {
                    loadedFiles.add(file);
                }
            }
        }

        updateFileTable(loadedFiles);
        analyticsLabel.setText("Loaded " + loadedFiles.size() + " files");
        // Switch to Browse tab
        mainTabPane.getSelectionModel().select(1);
    }

    private void updateFileTable(List<FileInfo> files) {
        ObservableList<FileInfo> data = FXCollections.observableArrayList(files);
        fileTable.setItems(data);
    }

    @FXML
    public void performSort() {
        if (loadedFiles == null || loadedFiles.isEmpty()) {
            showAlert("Error", "No data loaded.");
            return;
        }

        String criteria = sortCriteriaCombo2.getValue();
        boolean ascending = "Ascending".equals(sortOrderCombo.getValue());
        String algorithm = sortAlgorithmCombo.getValue();

        long startTime = System.nanoTime();
        organizer.sortFilesWithAlgorithm(loadedFiles, criteria, ascending, algorithm);
        long endTime = System.nanoTime();
        double timeMs = (endTime - startTime) / 1_000_000.0;

        updateFileTable(loadedFiles);
        analyticsLabel.setText(String.format("Sorted %d files using %s in %.2f ms", loadedFiles.size(), algorithm, timeMs));
    }

    @FXML
    public void applyFilter() {
        if (loadedFiles == null) return;

        List<FileInfo> filtered = new ArrayList<>();
        String type = filterTypeCombo.getValue();
        long minSize = 0, maxSize = Long.MAX_VALUE;
        try {
            if (!minSizeField.getText().isEmpty()) minSize = Long.parseLong(minSizeField.getText()) * 1024;
            if (!maxSizeField.getText().isEmpty()) maxSize = Long.parseLong(maxSizeField.getText()) * 1024;
        } catch (NumberFormatException e) {
            showAlert("Error", "Invalid size range.");
            return;
        }

        for (FileInfo file : loadedFiles) {
            boolean matchesType = type == null || "All".equals(type) || type.equalsIgnoreCase(file.getExtension());
            boolean matchesSize = file.getSize() >= minSize && file.getSize() <= maxSize;
            if (matchesType && matchesSize) {
                filtered.add(file);
            }
        }

        updateFileTable(filtered);
        analyticsLabel.setText("Filtered to " + filtered.size() + " files");
    }

    @FXML
    public void performSearch() {
        if (loadedFiles == null) return;

        String query = searchField.getText().toLowerCase();
        if (query.isEmpty()) {
            updateFileTable(loadedFiles);
            return;
        }

        List<FileInfo> results = new ArrayList<>();
        for (FileInfo file : loadedFiles) {
            if (file.getName().toLowerCase().contains(query) ||
                file.getExtension().toLowerCase().contains(query) ||
                file.getCategory().toLowerCase().contains(query)) {
                results.add(file);
            }
        }

        updateFileTable(results);
        analyticsLabel.setText("Found " + results.size() + " matches for '" + query + "'");
    }

    @FXML
    public void scanDuplicates() {
        if (scannedFiles == null || scannedFiles.isEmpty()) {
            showAlert("Error", "No files available. Please organize files first.");
            return;
        }

        String scope = duplicateScopeCombo.getValue();
        if (scope == null || scope.isEmpty()) {
            showAlert("Error", "Please select a scan scope.");
            return;
        }

        List<FileInfo> scopeFiles = new ArrayList<>();
        if ("Entire Workspace".equals(scope)) {
            scopeFiles.addAll(scannedFiles);
        } else {
            for (FileInfo file : scannedFiles) {
                if (scope.equals(file.getCategory())) {
                    scopeFiles.add(file);
                }
            }
        }

        new Thread(() -> {
            try {
                long startTime = System.nanoTime();
                Map<String, List<FileInfo>> duplicates = organizer.detectDuplicatesAdvanced(scopeFiles);
                long endTime = System.nanoTime();
                double timeMs = (endTime - startTime) / 1_000_000.0;

                duplicateGroups = duplicates;

                Platform.runLater(() -> {
                    updateDuplicateGroupsList(duplicates);
                    duplicateAnalyticsLabel.setText(String.format("Found %d duplicate groups in %.2f ms", duplicates.size(), timeMs));
                    // Switch to Duplicates tab
                    mainTabPane.getSelectionModel().select(2);
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showAlert("Error", "An error occurred: " + e.getMessage());
                });
            }
        }).start();
    }

    private void updateDuplicateGroupsList(Map<String, List<FileInfo>> duplicates) {
        ObservableList<String> groups = FXCollections.observableArrayList();
        for (String hash : duplicates.keySet()) {
            groups.add("Group: " + hash.substring(0, 8) + "... (" + duplicates.get(hash).size() + " files)");
        }
        duplicateGroupsListView.setItems(groups);
    }

    @FXML
    public void viewGroup() {
        String selected = duplicateGroupsListView.getSelectionModel().getSelectedItem();
        if (selected == null || duplicateGroups == null) return;

        // Extract hash from selected item (format: "Group: abc123... (3 files)")
        String hashPrefix = selected.substring(7, selected.indexOf("..."));
        String fullHash = null;
        for (String hash : duplicateGroups.keySet()) {
            if (hash.startsWith(hashPrefix)) {
                fullHash = hash;
                break;
            }
        }

        if (fullHash != null) {
            List<FileInfo> group = duplicateGroups.get(fullHash);
            for (FileInfo file : group) {
                file.setAction(group.indexOf(file) == 0 ? "Keep (Original)" : "Delete (Duplicate)");
            }
            ObservableList<FileInfo> data = FXCollections.observableArrayList(group);
            duplicateTable.setItems(data);
        }
    }

    @FXML
    public void deleteSelectedDuplicates() {
        ObservableList<FileInfo> items = duplicateTable.getItems();
        if (items.isEmpty()) {
            showAlert("Info", "No duplicates to delete.");
            return;
        }

        List<FileInfo> toDelete = new ArrayList<>();
        for (FileInfo file : items) {
            if ("Delete (Duplicate)".equals(file.getAction())) {
                toDelete.add(file);
            }
        }

        if (toDelete.isEmpty()) {
            showAlert("Info", "No files selected for deletion.");
            return;
        }

        // Create recovery folder
        Path recoveryDir = selectedDirectory.resolve("Sortify_Recovery");
        try {
            Files.createDirectories(recoveryDir);
        } catch (IOException e) {
            showAlert("Error", "Could not create recovery folder: " + e.getMessage());
            return;
        }

        int deletedCount = 0;
        long spaceRecovered = 0;

        for (FileInfo file : toDelete) {
            try {
                Path source = file.getPath();
                Path target = recoveryDir.resolve(source.getFileName());
                // Handle name conflicts in recovery
                int counter = 1;
                while (Files.exists(target)) {
                    String name = source.getFileName().toString();
                    String baseName = name.substring(0, name.lastIndexOf('.'));
                    String extension = name.substring(name.lastIndexOf('.'));
                    target = recoveryDir.resolve(baseName + "_rec" + counter + extension);
                    counter++;
                }

                Files.move(source, target);
                deletedCount++;
                spaceRecovered += file.getSize();

                // Remove from scannedFiles list
                scannedFiles.remove(file);

            } catch (IOException e) {
                logMessage("Failed to move file: " + file.getName() + " - " + e.getMessage());
            }
        }

        // Update UI
        duplicateTable.getItems().removeAll(toDelete);
        duplicateAnalyticsLabel.setText(duplicateAnalyticsLabel.getText() + " | Deleted: " + deletedCount + " files (" + formatSize(spaceRecovered) + " recovered)");
        showAlert("Success", "Successfully moved " + deletedCount + " duplicate files to recovery folder.");
    }

    @FXML
    public void recoverFiles() {
        if (selectedDirectory == null) {
            showAlert("Error", "No directory selected. Please select a directory first in the Organize tab.");
            return;
        }

        Path recoveryDir = selectedDirectory.resolve("Sortify_Recovery");
        if (!Files.exists(recoveryDir)) {
            showAlert("Info", "No recovery folder found.");
            return;
        }

        try {
            List<Path> recoveryFiles = Files.list(recoveryDir).collect(Collectors.toList());
            if (recoveryFiles.isEmpty()) {
                showAlert("Info", "Recovery folder is empty.");
                return;
            }

            int recoveredCount = 0;
            for (Path file : recoveryFiles) {
                try {
                    // Try to move back to original location
                    String fileName = file.getFileName().toString();
                    Path target;

                    // If it has "_rec" suffix, try to restore original name
                    if (fileName.contains("_rec")) {
                        String originalName = fileName.replaceAll("_rec\\d+", "");
                        target = selectedDirectory.resolve(originalName);
                    } else {
                        target = selectedDirectory.resolve(fileName);
                    }

                    // Handle conflicts
                    int counter = 1;
                    Path finalTarget = target;
                    while (Files.exists(finalTarget)) {
                        String name = target.getFileName().toString();
                        String baseName = name.substring(0, name.lastIndexOf('.'));
                        String extension = name.substring(name.lastIndexOf('.'));
                        finalTarget = target.getParent().resolve(baseName + "_restored" + counter + extension);
                        counter++;
                    }

                    Files.move(file, finalTarget);
                    recoveredCount++;

                    // Add back to scannedFiles if possible
                    try {
                        String recoveredFileName = finalTarget.getFileName().toString();
                        long fileSize = Files.size(finalTarget);
                        String extension = recoveredFileName.contains(".") ? recoveredFileName.substring(recoveredFileName.lastIndexOf(".")) : "";
                        LocalDateTime creationTime = LocalDateTime.now(); // Approximate
                        LocalDateTime modifiedTime = LocalDateTime.now();
                        FileInfo recoveredFile = new FileInfo(finalTarget, recoveredFileName, fileSize, extension, creationTime, modifiedTime);
                        if (scannedFiles != null) {
                            scannedFiles.add(recoveredFile);
                        }
                    } catch (IOException e) {
                        // Ignore, file was moved successfully
                    }

                } catch (IOException e) {
                    logMessage("Failed to recover file: " + file.getFileName() + " - " + e.getMessage());
                }
            }

            // Clean up empty recovery folder
            if (Files.list(recoveryDir).findAny().isEmpty()) {
                Files.delete(recoveryDir);
            }

            showAlert("Success", "Successfully recovered " + recoveredCount + " files.");

        } catch (IOException e) {
            showAlert("Error", "Failed to access recovery folder: " + e.getMessage());
        }
    }

    @FXML
    public void cancel() {
        previewVBox.setVisible(false);
        progressPreviewCard.setVisible(false);
        scannedFiles = null;
        detectedDuplicates = null;
        updateProgress("Cancelled.", 0.0);
    }

    @FXML private void exportReport() {
        String report = "Sortify Report\n" +
            "Files Processed: " + filesProcessedLabel.getText() + "\n" +
            "Duplicates Found: " + duplicatesRemovedLabel.getText() + "\n" +
            "Space Recovered: " + spaceRecoveredLabel.getText() + "\n" +
            "Time Taken: " + timeTakenLabel.getText() + "\n" +
            "Algorithmic Notes:\n" +
            "- Directory scanning uses DFS recursion for efficient traversal.\n" +
            "- Duplicate detection employs SHA-256 hashing (O(n) time complexity) instead of O(n²) comparisons.\n" +
            "- File sorting uses Merge Sort (stable, O(n log n)) for reliable ordering.\n" +
            "- HashMaps provide O(1) average lookup time for fast operations.\n" +
            "- Overall, algorithms chosen for scalability and performance on large datasets.\n";
        try {
            Files.write(Paths.get("sortify_report.txt"), report.getBytes());
            showAlert("Success", "Report exported to sortify_report.txt");
        } catch (IOException e) {
            showAlert("Error", "Failed to export report: " + e.getMessage());
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}