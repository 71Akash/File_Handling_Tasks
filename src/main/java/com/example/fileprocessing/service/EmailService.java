package com.example.fileprocessing.service;

import com.example.fileprocessing.entity.JobLog;
import com.example.fileprocessing.repository.JobLogRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final ProductPdfReportService pdfReportService;
    private final ProductExcelExportService excelExportService;
    private final TemplateEngine templateEngine;
    private final JobLogRepository jobLogRepository;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public EmailService(
            JavaMailSender mailSender,
            ProductPdfReportService pdfReportService,
            ProductExcelExportService excelExportService,
            TemplateEngine templateEngine,
            JobLogRepository jobLogRepository) {

        this.mailSender = mailSender;
        this.pdfReportService = pdfReportService;
        this.excelExportService = excelExportService;
        this.templateEngine = templateEngine;
        this.jobLogRepository = jobLogRepository;
    }

    public void sendInventoryReports(
                String recipient,
                String subject,
                String message) throws Exception {

        byte[] pdfReport = pdfReportService.generateProductReport();
        byte[] excelReport = excelExportService.exportProducts();

        // Retrieve the latest completed file import
        JobLog latestJob = jobLogRepository
                .findTopByJobNameStartingWithAndStatusOrderByExecutedAtDesc(
                        "FILE_IMPORT_",
                        "COMPLETED"
                )
                .orElse(null);

        int insertedCount = latestJob != null
                ? latestJob.getInsertedCount() : 0;

        int updatedCount = latestJob != null
                ? latestJob.getUpdatedCount() : 0;

        int errorCount = latestJob != null
                ? latestJob.getErrorCount() : 0;

        // Populate the HTML email template
        Context context = new Context();
        context.setVariable("insertedCount", insertedCount);
        context.setVariable("updatedCount", updatedCount);
        context.setVariable("errorCount", errorCount);

        String htmlContent = templateEngine.process(
                "email/inventory-report", context);

        MimeMessage mimeMessage = mailSender.createMimeMessage();

        MimeMessageHelper helper =
                new MimeMessageHelper(mimeMessage, true, "UTF-8");

        helper.setFrom(senderEmail);
        helper.setTo(recipient);
        helper.setSubject(subject);
        helper.setText(message, htmlContent);

        helper.addAttachment(
                "product_inventory_report.pdf",
                new ByteArrayResource(pdfReport));

        helper.addAttachment(
                "products_export.xlsx",
                new ByteArrayResource(excelReport));

        mailSender.send(mimeMessage);
        }
}