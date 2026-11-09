
package com.example.fileprocessing.service;

import com.example.fileprocessing.entity.Product;
import com.example.fileprocessing.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductXmlImportService {

    private final ProductRepository productRepository;

    public ProductXmlImportService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public XmlImportResult importProducts(MultipartFile file)
            throws Exception {

        int insertedCount = 0;
        int updatedCount = 0;
        int errorCount = 0;

        List<String> errors = new ArrayList<>();
        Set<Integer> seenProductIds = new HashSet<>();

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();

        // Secure XML parser configuration.
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false
        );
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false
        );

        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        try (InputStream inputStream = file.getInputStream()) {

            DocumentBuilder builder = factory.newDocumentBuilder();

            Document document = builder.parse(inputStream);
            document.getDocumentElement().normalize();

            Element root = document.getDocumentElement();

            if (!"products".equals(root.getTagName())) {
                throw new IllegalArgumentException(
                        "XML root element must be <products>"
                );
            }

            NodeList productNodes = root.getElementsByTagName("product");

            for (int i = 0; i < productNodes.getLength(); i++) {

                Node node = productNodes.item(i);

                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                Element productElement = (Element) node;

                try {

                    int productId = getInteger(
                            productElement, "product_id"
                    );

                    String productName = getText(
                            productElement, "product_name"
                    );

                    int categoryId = getInteger(
                            productElement, "category_id"
                    );

                    int supplierId = getInteger(
                            productElement, "supplier_id"
                    );

                    BigDecimal unitPrice = getDecimal(
                            productElement, "unit_price"
                    );

                    int stockQuantity = getInteger(
                            productElement, "stock_quantity"
                    );

                    int reorderLevel = getInteger(
                            productElement, "reorder_level"
                    );

                    // Check for duplicate IDs within this XML file.
                    if (!seenProductIds.add(productId)) {
                        throw new IllegalArgumentException(
                                "Duplicate product_id " + productId
                                + " in this XML file"
                        );
                    }

                    if (productId <= 0) {
                        throw new IllegalArgumentException(
                                "product_id must be greater than zero"
                        );
                    }

                    if (productName.isBlank()) {
                        throw new IllegalArgumentException(
                                "product_name cannot be blank"
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

                    if (reorderLevel < 0) {
                        throw new IllegalArgumentException(
                                "reorder_level cannot be negative"
                        );
                    }

                    boolean exists =
                            productRepository.existsById(productId);

                    Product product = new Product();

                    product.setProductId(productId);
                    product.setProductName(productName);
                    product.setCategoryId(categoryId);
                    product.setSupplierId(supplierId);
                    product.setUnitPrice(unitPrice);
                    product.setStockQuantity(stockQuantity);
                    product.setReorderLevel(reorderLevel);

                    // Insert new product or update existing product.
                    productRepository.saveAndFlush(product);

                    if (exists) {
                        updatedCount++;
                        System.out.println(
                                "XML row " + (i + 1)
                                + ": Product " + productId + " updated"
                        );
                    } else {
                        insertedCount++;
                        System.out.println(
                                "XML row " + (i + 1)
                                + ": Product " + productId + " inserted"
                        );
                    }

                } catch (Exception e) {

                    errorCount++;

                    String message = getUsefulErrorMessage(e);

                    String error =
                            "XML product " + (i + 1) + ": " + message;

                    errors.add(error);

                    System.err.println(error);
                }
            }
        }

        return new XmlImportResult(
                insertedCount,
                updatedCount,
                errorCount,
                errors
        );
    }

    private String getText(Element element, String tagName) {

        NodeList nodes = element.getElementsByTagName(tagName);

        if (nodes.getLength() == 0) {
            throw new IllegalArgumentException(
                    "Missing XML element: " + tagName
            );
        }

        String value = nodes.item(0).getTextContent().trim();

        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    tagName + " cannot be blank"
            );
        }

        return value;
    }

    private int getInteger(Element element, String tagName) {

        try {
            return new BigDecimal(
                    getText(element, tagName)
            ).intValueExact();

        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException(
                    tagName + " must be a valid integer"
            );
        }
    }

    private BigDecimal getDecimal(Element element, String tagName) {

        try {
            return new BigDecimal(getText(element, tagName));

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    tagName + " must be a valid number"
            );
        }
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

        return message.length() > 250
                ? message.substring(0, 250)
                : message;
    }

    public static class XmlImportResult {

        private final int insertedCount;
        private final int updatedCount;
        private final int errorCount;
        private final List<String> errors;

        public XmlImportResult(
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
