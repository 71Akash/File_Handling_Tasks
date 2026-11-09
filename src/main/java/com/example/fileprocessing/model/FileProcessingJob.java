
package com.example.fileprocessing.model;

import java.time.LocalDateTime;

public class FileProcessingJob {

    private final String jobId;
    private final String fileName;

    private volatile String status;
    private volatile Object result;
    private volatile String errorMessage;

    private final LocalDateTime createdAt;
    private volatile LocalDateTime completedAt;

    public FileProcessingJob(
        String jobId,
        String fileName,
        String status) {

        this.jobId = jobId;
        this.fileName = fileName;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public FileProcessingJob(String fileName) {

    this.jobId = java.util.UUID.randomUUID().toString();
    this.fileName = fileName;
    this.status = "PENDING";
    this.createdAt = LocalDateTime.now();
}

    public String getJobId() {
        return jobId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Object getResult() {
        return result;
    }

    public void setResult(Object result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
