
package com.example.fileprocessing.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
public class AsyncFileProcessingService {

    private final ExecutorService fileProcessingExecutor;

    private final ProductExcelImportService excelImportService;
    private final ProductXmlImportService xmlImportService;
    private final ProductJsonImportService jsonImportService;
    private final ProductCsvImportService productCsvImportService;
    
    public AsyncFileProcessingService(
            ExecutorService fileProcessingExecutor,
            ProductExcelImportService excelImportService,
            ProductXmlImportService xmlImportService,
            ProductJsonImportService jsonImportService,
            ProductCsvImportService productCsvImportService) {

        this.fileProcessingExecutor = fileProcessingExecutor;
        this.excelImportService = excelImportService;
        this.xmlImportService = xmlImportService;
        this.jsonImportService = jsonImportService;
        this.productCsvImportService = productCsvImportService;
    }


    public CompletableFuture<Object> processExcel(
            byte[] fileBytes, String filename) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                MultipartFile file = new InMemoryMultipartFile(
                        fileBytes,
                        "file",
                        filename,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                );

                return excelImportService.importProducts(file);

            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, fileProcessingExecutor);
    }

    public CompletableFuture<Object> processXml(
            byte[] fileBytes, String filename) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                MultipartFile file = new InMemoryMultipartFile(
                        fileBytes,
                        "file",
                        filename,
                        "application/xml"
                );

                return xmlImportService.importProducts(file);

            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, fileProcessingExecutor);
    }

    public CompletableFuture<Object> processJson(
            byte[] fileBytes, String filename) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                MultipartFile file = new InMemoryMultipartFile(
                        fileBytes,
                        "file",
                        filename,
                        "application/json"
                );

                return jsonImportService.importProducts(file);

            } catch (Exception e) {
                throw new java.util.concurrent.CompletionException(e);
            }
        }, fileProcessingExecutor);
    }

    public CompletableFuture<Object> processCsv(
            byte[] fileBytes,
            String filename) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                InMemoryMultipartFile file =
                        new InMemoryMultipartFile(
                                fileBytes,
                                "file",
                                filename,
                                "text/csv"
                        );

                return productCsvImportService.importProducts(file);

            } catch (Exception e) {
                throw new RuntimeException(
                        "CSV processing failed: " + e.getMessage(), e
                );
            }
        }, fileProcessingExecutor);
    }

}
