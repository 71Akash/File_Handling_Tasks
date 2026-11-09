
package com.example.fileprocessing.service;

import com.example.fileprocessing.entity.Product;
import com.example.fileprocessing.repository.ProductRepository;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

@Service
public class ProductCsvImportService {

    private final ProductRepository productRepository;

    public ProductCsvImportService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public CsvImportResult importProducts(MultipartFile file)
            throws Exception {

        int insertedCount = 0;
        int updatedCount = 0;
        int errorCount = 0;

        StringBuilder errors = new StringBuilder();
        Set<Integer> processedIds = new HashSet<>();

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CSV file is empty.");
        }

        try (Reader reader = new InputStreamReader(
                    file.getInputStream(), StandardCharsets.UTF_8);

             CSVParser csvParser = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreEmptyLines(true)
                    .setTrim(true)
                    .build()
                    .parse(reader)) {

            Set<String> requiredHeaders = Set.of(
                    "product_id",
                    "product_name",
                    "category_id",
                    "supplier_id",
                    "unit_price",
                    "stock_quantity"
            );

            if (!csvParser.getHeaderMap().keySet()
                    .containsAll(requiredHeaders)) {
                throw new IllegalArgumentException(
                        "CSV is missing one or more required columns."
                );
            }

            for (CSVRecord record : csvParser) {

                try {
                    int productId = Integer.parseInt(
                            record.get("product_id"));

                    if (!processedIds.add(productId)) {
                        throw new IllegalArgumentException(
                                "Duplicate product_id in CSV: " + productId);
                    }

                    String productName = record.get("product_name");

                    if (productName == null || productName.isBlank()) {
                        throw new IllegalArgumentException(
                                "Product name cannot be empty.");
                    }

                    int categoryId = Integer.parseInt(
                            record.get("category_id"));

                    int supplierId = Integer.parseInt(
                            record.get("supplier_id"));

                    BigDecimal unitPrice = new BigDecimal(
                            record.get("unit_price"));

                    int stockQuantity = Integer.parseInt(
                            record.get("stock_quantity"));

                    int reorderLevel = 5;

                    if (csvParser.getHeaderMap()
                            .containsKey("reorder_level")
                            && !record.get("reorder_level").isBlank()) {

                        reorderLevel = Integer.parseInt(
                                record.get("reorder_level"));
                    }

                    if (productId <= 0) {
                        throw new IllegalArgumentException(
                                "Product ID must be positive.");
                    }

                    if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException(
                                "Unit price must be greater than zero.");
                    }

                    if (stockQuantity < 0 || reorderLevel < 0) {
                        throw new IllegalArgumentException(
                                "Stock quantity and reorder level cannot be negative.");
                    }

                    Product product = productRepository
                            .findById(productId)
                            .orElse(new Product());

                    boolean isNew = !productRepository.existsById(productId);

                    product.setProductId(productId);
                    product.setProductName(productName);
                    product.setCategoryId(categoryId);
                    product.setSupplierId(supplierId);
                    product.setUnitPrice(unitPrice);
                    product.setStockQuantity(stockQuantity);
                    product.setReorderLevel(reorderLevel);

                    productRepository.saveAndFlush(product);

                    if (isNew) {
                        insertedCount++;
                    } else {
                        updatedCount++;
                    }

                } catch (Exception e) {
                    errorCount++;
                    errors.append("Record ")
                            .append(record.getRecordNumber())
                            .append(": ")
                            .append(e.getMessage())
                            .append("\n");
                }
            }
        }

        return new CsvImportResult(
                insertedCount,
                updatedCount,
                errorCount,
                errors.toString()
        );
    }

    public static class CsvImportResult {

        private final int insertedCount;
        private final int updatedCount;
        private final int errorCount;
        private final String errors;

        public CsvImportResult(
                int insertedCount,
                int updatedCount,
                int errorCount,
                String errors) {

            this.insertedCount = insertedCount;
            this.updatedCount = updatedCount;
            this.errorCount = errorCount;
            this.errors = errors;
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

        public String getErrors() {
            return errors;
        }
    }
}
