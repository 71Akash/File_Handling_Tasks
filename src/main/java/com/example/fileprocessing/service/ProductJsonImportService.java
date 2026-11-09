package com.example.fileprocessing.service;

import com.example.fileprocessing.entity.Product;
import com.example.fileprocessing.repository.ProductRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProductJsonImportService {

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper = new  ObjectMapper();

    public ProductJsonImportService(
            ProductRepository productRepository) {

        this.productRepository = productRepository;
    }

    public JsonImportResult importProducts(MultipartFile file)
            throws Exception {

        JsonNode root = objectMapper.readTree(file.getInputStream());

        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException(
                    "JSON file must contain an array of products."
            );
        }

        int insertedCount = 0;
        int updatedCount = 0;
        int errorCount = 0;

        List<String> errors = new ArrayList<>();
        Set<Integer> processedIds = new HashSet<>();

        int rowNumber = 0;

        for (JsonNode node : root) {
            rowNumber++;

            try {
                if (!node.isObject()) {
                    throw new IllegalArgumentException(
                            "Product entry must be a JSON object."
                    );
                }

                int productId = getRequiredInt(node, "product_id");
                String productName =
                        getRequiredText(node, "product_name");

                int categoryId = getRequiredInt(node, "category_id");
                int supplierId = getRequiredInt(node, "supplier_id");

                BigDecimal unitPrice =
                        getRequiredDecimal(node, "unit_price");

                int stockQuantity =
                        getRequiredInt(node, "stock_quantity");

                int reorderLevel =
                        node.has("reorder_level")
                                ? getRequiredInt(node, "reorder_level")
                                : 5;

                if (productId <= 0) {
                    throw new IllegalArgumentException(
                            "Product ID must be positive."
                    );
                }

                if (!processedIds.add(productId)) {
                    throw new IllegalArgumentException(
                            "Duplicate product ID in JSON file: "
                                    + productId
                    );
                }

                if (unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException(
                            "Unit price must be greater than zero."
                    );
                }

                if (stockQuantity < 0) {
                    throw new IllegalArgumentException(
                            "Stock quantity cannot be negative."
                    );
                }

                if (reorderLevel < 0) {
                    throw new IllegalArgumentException(
                            "Reorder level cannot be negative."
                    );
                }

                boolean exists =
                        productRepository.existsById(productId);

                Product product = exists
                        ? productRepository.findById(productId).orElseThrow()
                        : new Product();

                product.setProductId(productId);
                product.setProductName(productName);
                product.setCategoryId(categoryId);
                product.setSupplierId(supplierId);
                product.setUnitPrice(unitPrice);
                product.setStockQuantity(stockQuantity);
                product.setReorderLevel(reorderLevel);

                productRepository.saveAndFlush(product);

                if (exists) {
                    updatedCount++;
                } else {
                    insertedCount++;
                }

            } catch (Exception e) {
                errorCount++;

                String error =
                        "Product entry " + rowNumber + ": " + e.getMessage();

                errors.add(error);
                System.err.println(error);
            }
        }

        return new JsonImportResult(
                insertedCount,
                updatedCount,
                errorCount,
                errors
        );
    }

    private int getRequiredInt(JsonNode node, String field) {

        JsonNode value = node.get(field);

        if (value == null || !value.isIntegralNumber()
                || !value.canConvertToInt()) {
            throw new IllegalArgumentException(
                    "Missing or invalid integer field: " + field
            );
        }

        return value.intValue();
    }

    private String getRequiredText(JsonNode node, String field) {

        JsonNode value = node.get(field);

        if (value == null || !value.isTextual()
                || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Missing or invalid text field: " + field
            );
        }

        return value.asText().trim();
    }

    private BigDecimal getRequiredDecimal(
            JsonNode node, String field) {

        JsonNode value = node.get(field);

        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(
                    "Missing or invalid numeric field: " + field
            );
        }

        return value.decimalValue();
    }

    public static class JsonImportResult {

        private final int insertedCount;
        private final int updatedCount;
        private final int errorCount;
        private final List<String> errors;

        public JsonImportResult(
                int insertedCount,
                int updatedCount,
                int errorCount,
                List<String> errors) {

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

        public List<String> getErrors() {
            return errors;
        }
    }
}
