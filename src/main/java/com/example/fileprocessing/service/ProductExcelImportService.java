
package com.example.fileprocessing.service;

import com.example.fileprocessing.entity.Product;
import com.example.fileprocessing.repository.ProductRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ProductExcelImportService {

    private final ProductRepository productRepository;

    public ProductExcelImportService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    private boolean isRowEmpty(Row row, DataFormatter formatter) {

        if (row == null) {
            return true;
        }

        for (int columnIndex = row.getFirstCellNum();
            columnIndex < row.getLastCellNum();
            columnIndex++) {

            Cell cell = row.getCell(columnIndex);

            if (cell != null &&
                    !formatter.formatCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }

        return true;
}

    public ImportResult importProducts(MultipartFile file) throws Exception {

        int insertedCount = 0;
        int updatedCount = 0;
        int errorCount = 0;

        Set<Integer> seenProductIds = new HashSet<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheet("Products");

            if (sheet == null) {
                throw new IllegalArgumentException(
                        "Excel file must contain a sheet named Products"
                );
            }

            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new IllegalArgumentException("Excel file is empty");
            }

            DataFormatter formatter = new DataFormatter();
            Map<String, Integer> columns = new HashMap<>();

            for (Cell cell : headerRow) {
                String header = formatter.formatCellValue(cell)
                        .trim()
                        .toLowerCase();

                columns.put(header, cell.getColumnIndex());
            }

            String[] requiredHeaders = {
                    "product_id",
                    "product_name",
                    "category_id",
                    "supplier_id",
                    "unit_price",
                    "stock_quantity"
            };

            for (String header : requiredHeaders) {
                if (!columns.containsKey(header)) {
                    throw new IllegalArgumentException(
                            "Missing required Excel column: " + header
                    );
                }
            }

            // Add the status column if it doesn't already exist.
            int statusColumn;

            if (columns.containsKey("import status")) {
                statusColumn = columns.get("import status");
            } else {
                statusColumn = headerRow.getLastCellNum();
                headerRow.createCell(statusColumn)
                        .setCellValue("Import Status");
            }

            for (int rowIndex = 1;
                 rowIndex <= sheet.getLastRowNum();
                 rowIndex++) {

                Row row = sheet.getRow(rowIndex);

                if (row == null || isRowEmpty(row, formatter)) {
                    continue;
                }

                Cell statusCell = row.getCell(
                        statusColumn,
                        Row.MissingCellPolicy.CREATE_NULL_AS_BLANK
                );

                try {
                    // Reject rows containing any blank required field.
                    for (String header : requiredHeaders) {
                        String value = getString(
                                row,
                                columns.get(header),
                                formatter
                        );

                        if (value.isBlank()) {
                            throw new IllegalArgumentException(
                                    header + " is blank"
                            );
                        }
                    }

                    int productId = getInteger(
                            row, columns.get("product_id"), formatter
                    );

                    String productName = getString(
                            row, columns.get("product_name"), formatter
                    );

                    int categoryId = getInteger(
                            row, columns.get("category_id"), formatter
                    );

                    int supplierId = getInteger(
                            row, columns.get("supplier_id"), formatter
                    );

                    BigDecimal unitPrice = getDecimal(
                            row, columns.get("unit_price"), formatter
                    );

                    int stockQuantity = getInteger(
                            row, columns.get("stock_quantity"), formatter
                    );

                    // Detect duplicate IDs within this uploaded workbook.
                    if (!seenProductIds.add(productId)) {
                        throw new IllegalArgumentException(
                                "Duplicate product_id " + productId
                                + " in this Excel file"
                        );
                    }

                    if (productId <= 0) {
                        throw new IllegalArgumentException(
                                "product_id must be greater than zero"
                        );
                    }

                    if (productName.isBlank()) {
                        throw new IllegalArgumentException(
                                "product_name is blank"
                        );
                    }

                    if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException(
                                "unit_price must be greater than zero"
                        );
                    }

                    if (stockQuantity < 0) {
                        throw new IllegalArgumentException(
                                "stock_quantity cannot be negative"
                        );
                    }

                    boolean exists = productRepository
                            .existsById(productId);

                    Product product = new Product();

                    product.setProductId(productId);
                    product.setProductName(productName);
                    product.setCategoryId(categoryId);
                    product.setSupplierId(supplierId);
                    product.setUnitPrice(unitPrice);
                    product.setStockQuantity(stockQuantity);

                    // Default value because the Excel file doesn't contain it.
                    product.setReorderLevel(5);

                    // Flush now so database errors are caught for this row.
                    productRepository.saveAndFlush(product);

                    if (exists) {
                        updatedCount++;
                        statusCell.setCellValue("Updated");
                    } else {
                        insertedCount++;
                        statusCell.setCellValue("Inserted");
                    }

                } catch (Exception e) {
                    errorCount++;

                    String errorMessage = getUsefulErrorMessage(e);

                    String status = "ERROR: " + errorMessage;

                    statusCell.setCellValue(status);

                    System.err.println(
                            "Excel row " + (rowIndex + 1)
                            + " failed: " + errorMessage
                    );
                }
            }

            // Write the status column into a new Excel file in memory.
            ByteArrayOutputStream outputStream =
                    new ByteArrayOutputStream();

            workbook.write(outputStream);

            return new ImportResult(
                    outputStream.toByteArray(),
                    insertedCount,
                    updatedCount,
                    errorCount
            );
        }
    }

    private String getString(
            Row row,
            int columnIndex,
            DataFormatter formatter) {

        Cell cell = row.getCell(columnIndex);

        if (cell == null) {
            return "";
        }

        return formatter.formatCellValue(cell).trim();
    }

    private int getInteger(
            Row row,
            int columnIndex,
            DataFormatter formatter) {

        String value = getString(row, columnIndex, formatter);

        return new BigDecimal(value).intValueExact();
    }

    private BigDecimal getDecimal(
            Row row,
            int columnIndex,
            DataFormatter formatter) {

        String value = getString(row, columnIndex, formatter);

        return new BigDecimal(value);
    }

    private String getUsefulErrorMessage(Exception e) {

        Throwable cause = e;

        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        String message = cause.getMessage();

        if (message == null || message.isBlank()) {
            message = e.getMessage();
        }

        if (message == null || message.isBlank()) {
            return "Unknown error";
        }

        // Keep Excel cell messages readable.
        return message.length() > 250
                ? message.substring(0, 250)
                : message;
    }

    public static class ImportResult {

        private final byte[] excelFile;
        private final int insertedCount;
        private final int updatedCount;
        private final int errorCount;

        public ImportResult(
                byte[] excelFile,
                int insertedCount,
                int updatedCount,
                int errorCount) {

            this.excelFile = excelFile;
            this.insertedCount = insertedCount;
            this.updatedCount = updatedCount;
            this.errorCount = errorCount;
        }

        public byte[] getExcelFile() {
            return excelFile;
        }

        public int getInsertedCount() {
            return insertedCount;
        }

        public int getUpdatedCount() {
            return updatedCount;
        }

        public int getErrorCount() {
            return errorCount;
        }
    }
}
