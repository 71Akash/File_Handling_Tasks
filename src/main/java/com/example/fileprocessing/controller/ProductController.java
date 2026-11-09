package com.example.fileprocessing.controller;

import com.example.fileprocessing.entity.Product;
import com.example.fileprocessing.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.example.fileprocessing.service.ProductExcelExportService;
import com.example.fileprocessing.service.ProductExcelImportService;
import com.example.fileprocessing.service.ProductPdfReportService;
import com.example.fileprocessing.service.ProductXmlImportService;
import com.example.fileprocessing.service.ProductXmlImportService;
import com.example.fileprocessing.service.ProductExcelExportService;
import com.example.fileprocessing.service.ProductPdfReportService;
import com.example.fileprocessing.service.EmailService;
import com.example.fileprocessing.service.ProductJsonImportService;
import com.example.fileprocessing.service.FileProcessingJobService;
import com.example.fileprocessing.model.FileProcessingJob;
import com.example.fileprocessing.entity.JobLog;
import com.example.fileprocessing.repository.JobLogRepository;
import com.example.fileprocessing.service.ProductCsvImportService;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductExcelImportService productExcelImportService;
    private final ProductXmlImportService productXmlImportService;
    private final ProductExcelExportService productExcelExportService;
    private final ProductPdfReportService productPdfReportService;
    private final EmailService emailService;
    private final ProductJsonImportService jsonImportService;
    private final ProductCsvImportService productCsvImportService;
    private final FileProcessingJobService fileProcessingJobService;
    
    public ProductController(
            ProductRepository productRepository,
            ProductExcelImportService productExcelImportService,
            ProductXmlImportService productXmlImportService,
            ProductExcelExportService productExcelExportService,
            ProductPdfReportService productPdfReportService,
            EmailService emailService,
            ProductJsonImportService jsonImportService, 
            ProductCsvImportService productCsvImportService,
            FileProcessingJobService fileProcessingJobService) {

        this.productRepository = productRepository;
        this.productExcelImportService = productExcelImportService;
        this.productXmlImportService = productXmlImportService;
        this.productExcelExportService = productExcelExportService;
        this.productPdfReportService = productPdfReportService;
        this.emailService = emailService;
        this.jsonImportService = jsonImportService;
        this.productCsvImportService = productCsvImportService;
        this.fileProcessingJobService = fileProcessingJobService;
    }

    @PostMapping(
            value = "/upload-xml",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ProductXmlImportService.XmlImportResult uploadXml(
            @RequestParam("file") MultipartFile file) throws Exception {

        if (file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please upload a non-empty XML file"
            );
        }

        return productXmlImportService.importProducts(file);
    }

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<byte[]> uploadProducts(
            @RequestParam("file") MultipartFile file) throws Exception {

        if (file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Please upload a non-empty Excel file"
            );
        }

        ProductExcelImportService.ImportResult result =
                productExcelImportService.importProducts(file);

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"import_result.xlsx\""
                )
                .header(
                        "X-Inserted-Count",
                        String.valueOf(result.getInsertedCount())
                )
                .header(
                        "X-Updated-Count",
                        String.valueOf(result.getUpdatedCount())
                )
                .header(
                        "X-Error-Count",
                        String.valueOf(result.getErrorCount())
                )
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                )
                .body(result.getExcelFile());
    }

    @PostMapping("/email-report")
    public ResponseEntity<String> emailInventoryReport(
            @RequestParam String recipient,
            @RequestParam(defaultValue = "Inventory Report") String subject,
            @RequestParam(defaultValue = "Please find the latest inventory reports attached.") String message) {

        try {
            emailService.sendInventoryReports(recipient, subject, message);

            return ResponseEntity.ok(
                    "Inventory PDF and Excel reports sent successfully to " + recipient
            );

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.internalServerError()
                    .body("Failed to send email: " + e.getMessage());
        }
    }
 
    @PostMapping("/upload-json")
    public ResponseEntity<?> uploadJson(
            @RequestParam("file") MultipartFile file) {

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("Please select a JSON file.");
            }

            ProductJsonImportService.JsonImportResult result =
                    jsonImportService.importProducts(file);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("JSON import failed: " + e.getMessage());
        }
    }

    // Get all products
    @GetMapping
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    // Get one product by ID
    @GetMapping("/{id}")
    public Product getProductById(@PathVariable Integer id) {
        return productRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Product not found with ID: " + id
                        )
                );
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportProducts() throws Exception {

        byte[] excelFile = productExcelExportService.exportProducts();

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"products_export.xlsx\""
                )
                .header(
                        "X-Total-Products",
                        String.valueOf(productRepository.count())
                )
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                )
                .body(excelFile);
    }

    @GetMapping("/report/pdf")
    public ResponseEntity<byte[]> generatePdfReport() throws Exception {

        byte[] pdfFile = productPdfReportService.generateProductReport();

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"product_inventory_report.pdf\""
                )
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfFile);
    }

        
    @PostMapping("/async/upload")
    public ResponseEntity<?> asyncExcelUpload(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Please select an Excel file.");
        }

        FileProcessingJob job = fileProcessingJobService.submitExcel(
                file.getBytes(),
                file.getOriginalFilename()
        );

        return ResponseEntity.accepted().body(job);
    }


    @PostMapping("/async/upload-xml")
    public ResponseEntity<?> asyncXmlUpload(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Please select an XML file.");
        }

        FileProcessingJob job = fileProcessingJobService.submitXml(
                file.getBytes(),
                file.getOriginalFilename()
        );

        return ResponseEntity.accepted().body(job);
    }


    @PostMapping("/async/upload-json")
    public ResponseEntity<?> asyncJsonUpload(
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body("Please select a JSON file.");
        }

        FileProcessingJob job = fileProcessingJobService.submitJson(
                file.getBytes(),
                file.getOriginalFilename()
        );

        return ResponseEntity.accepted().body(job);
    }

    @PostMapping(
            value = "/async/upload-csv",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> uploadCsvAsync(
            @RequestParam("file") MultipartFile file) {

        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body("Please upload a valid CSV file.");
            }

            String filename = file.getOriginalFilename();

            if (filename == null ||
                    !filename.toLowerCase().endsWith(".csv")) {

                return ResponseEntity.badRequest()
                        .body("Only CSV files are allowed.");
            }

            FileProcessingJob job =
                    fileProcessingJobService.submitCsv(
                            file.getBytes(),
                            filename
                    );

            return ResponseEntity.accepted().body(job);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to submit CSV job: " + e.getMessage());
        }
    }

    @GetMapping("/async/jobs/{jobId}")
    public ResponseEntity<?> getJobStatus(
            @PathVariable String jobId) {

        FileProcessingJob job =
                fileProcessingJobService.getJob(jobId);

        if (job == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(job);
    }
}
