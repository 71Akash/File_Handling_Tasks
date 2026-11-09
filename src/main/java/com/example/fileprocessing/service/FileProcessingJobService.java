
package com.example.fileprocessing.service;

import com.example.fileprocessing.entity.JobLog;
import com.example.fileprocessing.model.FileProcessingJob;
import com.example.fileprocessing.repository.JobLogRepository;
import com.example.fileprocessing.service.ProductCsvImportService;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileProcessingJobService {

    private final AsyncFileProcessingService asyncService;
    private final JobLogRepository jobLogRepository;

    private final Map<String, FileProcessingJob> jobs =
            new ConcurrentHashMap<>();

    public FileProcessingJobService(
            AsyncFileProcessingService asyncService,
            JobLogRepository jobLogRepository) {

        this.asyncService = asyncService;
        this.jobLogRepository = jobLogRepository;
    }

    public FileProcessingJob submitExcel(
            byte[] bytes, String filename) {

        return submitJob(bytes, filename, "EXCEL");
    }

    public FileProcessingJob submitXml(
            byte[] bytes, String filename) {

        return submitJob(bytes, filename, "XML");
    }

    public FileProcessingJob submitJson(
            byte[] bytes, String filename) {

        return submitJob(bytes, filename, "JSON");
    }

    public FileProcessingJob submitCsv(
            byte[] bytes, String filename) {

        return submitJob(bytes, filename, "CSV");
    }

    private FileProcessingJob submitJob(
            byte[] bytes,
            String filename,
            String fileType) {

        FileProcessingJob job =
                new FileProcessingJob(filename);

        jobs.put(job.getJobId(), job);

        JobLog log = new JobLog();

        log.setJobId(job.getJobId());
        log.setJobName("FILE_IMPORT_" + fileType);
        log.setFileName(filename);
        log.setFileType(fileType);
        log.setStatus("PENDING");
        log.setExecutedAt(LocalDateTime.now());

        // Save initial job record
        jobLogRepository.saveAndFlush(log);

        try {
            // Update status before submitting the task
            job.setStatus("PROCESSING");
            log.setStatus("PROCESSING");

            jobLogRepository.saveAndFlush(log);

            CompletableFuture<Object> future;

            switch (fileType) {
                case "EXCEL":
                    future = asyncService.processExcel(bytes, filename);
                    break;

                case "XML":
                    future = asyncService.processXml(bytes, filename);
                    break;

                case "JSON":
                    future = asyncService.processJson(bytes, filename);
                    break;
                
                case "CSV":
                    future = asyncService.processCsv(bytes, filename);
                    break;

                default:
                    throw new IllegalArgumentException(
                            "Unsupported file type: " + fileType
                    );
            }

            future.whenComplete((result, error) ->
                    completeJob(job, log, result, error));

        } catch (Exception e) {
            failJob(job, log, e);
        }

        return job;
    }

    private void completeJob(
            FileProcessingJob job,
            JobLog log,
            Object result,
            Throwable error) {

        if (error != null) {

            Throwable cause =
                    error instanceof CompletionException
                    && error.getCause() != null
                            ? error.getCause()
                            : error;

            failJob(job, log, cause);
            return;
        }

        job.setResult(result);
        job.setStatus("COMPLETED");
        job.setCompletedAt(LocalDateTime.now());

        log.setStatus("COMPLETED");
        log.setCompletedAt(LocalDateTime.now());

        updateImportCounts(log, result);

        jobLogRepository.save(log);
    }

    private void failJob(
            FileProcessingJob job,
            JobLog log,
            Throwable error) {

        String message = error.getMessage() != null
                ? error.getMessage()
                : "Unknown processing error";

        job.setErrorMessage(message);
        job.setStatus("FAILED");
        job.setCompletedAt(LocalDateTime.now());

        log.setStatus("FAILED");
        log.setErrorMessage(message);
        log.setCompletedAt(LocalDateTime.now());

        jobLogRepository.save(log);
    }

    private void updateImportCounts(
            JobLog log, Object result) {

        if (result instanceof
                ProductExcelImportService.ImportResult) {

            ProductExcelImportService.ImportResult r =
                    (ProductExcelImportService.ImportResult) result;

            log.setInsertedCount(r.getInsertedCount());
            log.setUpdatedCount(r.getUpdatedCount());
            log.setErrorCount(r.getErrorCount());

        } else if (result instanceof
                ProductXmlImportService.XmlImportResult) {

            ProductXmlImportService.XmlImportResult r =
                    (ProductXmlImportService.XmlImportResult) result;

            log.setInsertedCount(r.getInsertedCount());
            log.setUpdatedCount(r.getUpdatedCount());
            log.setErrorCount(r.getErrorCount());

        } else if (result instanceof
                ProductJsonImportService.JsonImportResult) {

            ProductJsonImportService.JsonImportResult r =
                    (ProductJsonImportService.JsonImportResult) result;

            log.setInsertedCount(r.getInsertedCount());
            log.setUpdatedCount(r.getUpdatedCount());
            log.setErrorCount(r.getErrorCount());
        } else if (result instanceof
                ProductCsvImportService.CsvImportResult) {

            ProductCsvImportService.CsvImportResult r =
                    (ProductCsvImportService.CsvImportResult) result;

            log.setInsertedCount(r.getInsertedCount());
            log.setUpdatedCount(r.getUpdatedCount());
            log.setErrorCount(r.getErrorCount());
        }
    }

    public FileProcessingJob getJob(String jobId) {

        FileProcessingJob job = jobs.get(jobId);

        if (job != null) {
            return job;
        }

        return jobLogRepository.findByJobId(jobId)
                .map(log -> {

                    FileProcessingJob historicalJob =
                            new FileProcessingJob(
                                    log.getJobId(),
                                    log.getFileName(),
                                    log.getStatus()
                            );

                    historicalJob.setErrorMessage(
                            log.getErrorMessage()
                    );

                    historicalJob.setCompletedAt(
                            log.getCompletedAt()
                    );

                    return historicalJob;
                })
                .orElse(null);
    }
}
