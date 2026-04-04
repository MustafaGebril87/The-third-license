package com.thethirdlicense.controllers;

import java.util.List;
import java.util.UUID;

public class MergeResolveRequest {
    private UUID repositoryId;
    private String branch;
    private List<FileMergeItem> files;
    private String mergeType; // "MERGE_REQUEST" or "PULL_CONFLICT"

    public String getMergeType() {
        return mergeType;
    }
    public void setMergeType(String mergeType) {
        this.mergeType = mergeType;
    }

    // Nested static class to represent each file
    public static class FileMergeItem {
        private String filePath;
        private String mergedContent;

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getMergedContent() {
            return mergedContent;
        }

        public void setMergedContent(String mergedContent) {
            this.mergedContent = mergedContent;
        }
    }

    // Getters and setters
    public UUID getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(UUID repositoryId) {
        this.repositoryId = repositoryId;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public List<FileMergeItem> getFiles() {
        return files;
    }

    public void setFiles(List<FileMergeItem> files) {
        this.files = files;
    }
}
