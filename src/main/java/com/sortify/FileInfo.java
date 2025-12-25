package com.sortify;

import java.nio.file.Path;
import java.time.LocalDateTime;

public class FileInfo {
    private Path path;
    private String name;
    private long size;
    private String extension;
    private LocalDateTime creationDate;
    private LocalDateTime lastModifiedDate;
    private String hash;
    private String category;
    private String action;

    public FileInfo(Path path, String name, long size, String extension, LocalDateTime creationDate, LocalDateTime lastModifiedDate) {
        this.path = path;
        this.name = name;
        this.size = size;
        this.extension = extension;
        this.creationDate = creationDate;
        this.lastModifiedDate = lastModifiedDate;
    }

    // Getters and setters
    public Path getPath() { return path; }
    public void setPath(Path path) { this.path = path; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }

    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }

    public LocalDateTime getCreationDate() { return creationDate; }
    public void setCreationDate(LocalDateTime creationDate) { this.creationDate = creationDate; }

    public LocalDateTime getLastModifiedDate() { return lastModifiedDate; }
    public void setLastModifiedDate(LocalDateTime lastModifiedDate) { this.lastModifiedDate = lastModifiedDate; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getSizeKB() {
        return String.format("%.2f", size / 1024.0);
    }
}