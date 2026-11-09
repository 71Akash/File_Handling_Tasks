
package com.example.fileprocessing.service;

import com.example.fileprocessing.entity.Product;
import com.example.fileprocessing.repository.ProductRepository;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Image;
import com.lowagie.text.Phrase;

import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ProductPdfReportService {

    private final ProductRepository productRepository;

    public ProductPdfReportService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public byte[] generateProductReport() throws Exception {

        List<Product> products = productRepository.findAll();

        int totalProducts = products.size();
        int lowStockCount = 0;
        int inStockCount = 0;

        for (Product product : products) {

            Integer quantity = product.getStockQuantity();
            Integer reorderLevel = product.getReorderLevel();

            if (quantity != null && reorderLevel != null
                    && quantity <= reorderLevel) {
                lowStockCount++;
            } else {
                inStockCount++;
            }
        }

        try (ByteArrayOutputStream outputStream =
                     new ByteArrayOutputStream()) {

            Document document = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);

            PdfWriter.getInstance(document, outputStream);

            document.open();

            Font titleFont = new Font(
                    Font.HELVETICA, 20, Font.BOLD
            );

            Font normalFont = new Font(
                    Font.HELVETICA, 10, Font.NORMAL
            );

            Font headerFont = new Font(
                    Font.HELVETICA, 9, Font.BOLD,
                    java.awt.Color.WHITE
            );

            Font tableFont = new Font(
                    Font.HELVETICA, 8, Font.NORMAL
            );

            // Report title
            Paragraph title = new Paragraph(
                    "PRODUCT INVENTORY REPORT",
                    titleFont
            );

            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            document.add(new Paragraph(" "));

            String generatedDate = LocalDateTime.now().format(
                    DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")
            );

            Paragraph date = new Paragraph(
                    "Generated on: " + generatedDate,
                    normalFont
            );

            date.setAlignment(Element.ALIGN_CENTER);
            document.add(date);

            document.add(new Paragraph(" "));

            // Optional sample image
            try {
                ClassPathResource imageResource =
                        new ClassPathResource(
                                "static/images/inventory-sample.png"
                        );

                if (imageResource.exists()) {

                    try (InputStream imageStream =
                                 imageResource.getInputStream()) {

                        Image sampleImage = Image.getInstance(
                                imageStream.readAllBytes()
                        );

                        sampleImage.scaleToFit(650, 150);
                        sampleImage.setAlignment(Element.ALIGN_CENTER);

                        document.add(sampleImage);
                        document.add(new Paragraph(" "));
                    }
                }

            } catch (Exception e) {
                System.err.println(
                        "Sample image could not be added: " + e.getMessage()
                );
            }

            // Inventory summary
            PdfPTable summaryTable = new PdfPTable(3);
            summaryTable.setWidthPercentage(100);
            summaryTable.setSpacingAfter(18);

            addSummaryCell(
                    summaryTable, "Total Products", totalProducts
            );

            addSummaryCell(
                    summaryTable, "In Stock", inStockCount
            );

            addSummaryCell(
                    summaryTable, "Low Stock", lowStockCount
            );

            document.add(summaryTable);

            // Product details table
            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);

            table.setWidths(new float[]{
                    1.0f, 2.0f, 1.0f, 1.0f, 1.3f,
                    1.0f, 1.1f, 1.4f, 1.2f
            });

            String[] headers = {
                    "Product ID",
                    "Product Name",
                    "Category ID",
                    "Supplier ID",
                    "Unit Price",
                    "Stock Qty",
                    "Reorder Level",
                    "Weight (KG)",
                    "Stock Status"
            };

            for (String header : headers) {
                PdfPCell cell = new PdfPCell(
                        new Phrase(header, headerFont)
                );

                cell.setBackgroundColor(
                        new java.awt.Color(31, 78, 121)
                );

                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(7);

                table.addCell(cell);
            }

            for (Product product : products) {

                addTableCell(table, product.getProductId(), tableFont);
                addTableCell(table, product.getProductName(), tableFont);
                addTableCell(table, product.getCategoryId(), tableFont);
                addTableCell(table, product.getSupplierId(), tableFont);
                addTableCell(table, product.getUnitPrice(), tableFont);
                addTableCell(table, product.getStockQuantity(), tableFont);
                addTableCell(table, product.getReorderLevel(), tableFont);
                addTableCell(table, product.getWeightKg(), tableFont);

                boolean lowStock =
                        product.getStockQuantity() != null
                        && product.getReorderLevel() != null
                        && product.getStockQuantity()
                        <= product.getReorderLevel();

                PdfPCell statusCell = new PdfPCell(
                        new Phrase(
                                lowStock ? "LOW STOCK" : "IN STOCK",
                                tableFont
                        )
                );

                statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                statusCell.setPadding(5);

                if (lowStock) {
                    statusCell.setBackgroundColor(
                            new java.awt.Color(255, 220, 220)
                    );
                } else {
                    statusCell.setBackgroundColor(
                            new java.awt.Color(220, 245, 220)
                    );
                }

                table.addCell(statusCell);
            }

            document.add(table);

            document.close();

            return outputStream.toByteArray();
        }
    }

    private void addSummaryCell(
            PdfPTable table,
            String label,
            int value) {

        Font font = new Font(
                Font.HELVETICA, 11, Font.BOLD
        );

        PdfPCell cell = new PdfPCell(
                new Phrase(label + "\n" + value, font)
        );

        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(12);

        table.addCell(cell);
    }

    private void addTableCell(
            PdfPTable table,
            Object value,
            Font font) {

        String text = value == null ? "-" : value.toString();

        PdfPCell cell = new PdfPCell(
                new Phrase(text, font)
        );

        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        table.addCell(cell);
    }
}
