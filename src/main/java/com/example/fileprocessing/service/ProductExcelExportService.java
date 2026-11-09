
package com.example.fileprocessing.service;

import com.example.fileprocessing.entity.Product;
import com.example.fileprocessing.repository.ProductRepository;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class ProductExcelExportService {

    private final ProductRepository productRepository;

    public ProductExcelExportService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public byte[] exportProducts() throws Exception {

        List<Product> products = productRepository.findAll();

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream =
                     new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Products");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            String[] headers = {
                    "Product ID",
                    "Product Name",
                    "Category ID",
                    "Supplier ID",
                    "Unit Price",
                    "Stock Quantity",
                    "Reorder Level",
                    "Created Date",
                    "Weight (KG)"
            };

            Row headerRow = sheet.createRow(0);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Product records
            int rowNumber = 1;

            for (Product product : products) {

                Row row = sheet.createRow(rowNumber++);

                row.createCell(0).setCellValue(
                        product.getProductId()
                );

                row.createCell(1).setCellValue(
                        product.getProductName()
                );

                setIntegerCell(row, 2, product.getCategoryId());
                setIntegerCell(row, 3, product.getSupplierId());

                setDecimalCell(row, 4, product.getUnitPrice());

                setIntegerCell(row, 5, product.getStockQuantity());
                setIntegerCell(row, 6, product.getReorderLevel());

                LocalDate createdDate = product.getCreatedDate();

                if (createdDate != null) {
                    row.createCell(7).setCellValue(createdDate.toString());
                }

                setDecimalCell(row, 8, product.getWeightKg());
            }

            // Format the worksheet
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(
                    new org.apache.poi.ss.util.CellRangeAddress(
                            0, Math.max(0, products.size()), 0, 8
                    )
            );

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);

            return outputStream.toByteArray();
        }
    }

    private void setIntegerCell(
            Row row, int column, Integer value) {

        if (value != null) {
            row.createCell(column).setCellValue(value);
        }
    }

    private void setDecimalCell(
            Row row, int column, BigDecimal value) {

        if (value != null) {
            row.createCell(column).setCellValue(value.doubleValue());
        }
    }
}
