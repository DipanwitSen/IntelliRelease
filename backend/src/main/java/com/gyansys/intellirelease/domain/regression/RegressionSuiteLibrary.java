package com.gyansys.intellirelease.domain.regression;

import com.gyansys.intellirelease.model.enums.SapCapability;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Capability to regression suite mapping. Curated alongside a QA lead, not
 * derived — the suite names have to match what is actually in the test plan or
 * the recommendation is unusable.
 */
@Component
public class RegressionSuiteLibrary {

    public static final String LIBRARY_VERSION = "regression-suites-2026.1";

    private final Map<SapCapability, List<String>> suites = build();

    public List<String> suitesFor(SapCapability capability) {
        return suites.getOrDefault(capability, List.of());
    }

    private static Map<SapCapability, List<String>> build() {
        Map<SapCapability, List<String>> map = new EnumMap<>(SapCapability.class);

        map.put(SapCapability.CHECKOUT_CAPABILITY, List.of(
                "Guest Checkout", "Registered Checkout", "Coupon Flow",
                "Cart Merge", "Saved Cart", "Payment"));

        map.put(SapCapability.CART_CAPABILITY, List.of(
                "Add to Cart", "Remove from Cart", "Update Quantity",
                "Cart Merge", "Guest Cart", "Saved Cart"));

        map.put(SapCapability.SEARCH_CONFIGURATION, List.of(
                "Search", "PLP", "Autosuggest", "Facets", "Category Navigation"));

        map.put(SapCapability.SEARCH, List.of(
                "Search", "PLP", "Autosuggest", "Facets"));

        map.put(SapCapability.OCC_API, List.of(
                "OCC Contract Tests", "Spartacus Integration", "Mobile API Tests"));

        map.put(SapCapability.CMS_CAPABILITY, List.of(
                "Content Rendering", "Homepage", "Personalization", "Content Slots"));

        map.put(SapCapability.PAYMENT, List.of(
                "Card Payment", "Retry Payment", "Failed Payment", "Refund", "Payment Gateway"));

        map.put(SapCapability.TYPE_SYSTEM, List.of(
                "Data Model Verification", "Migration Integrity", "Solr Reindex Verification"));

        map.put(SapCapability.SECURITY, List.of(
                "Login", "Session Expiry", "Token Refresh", "RBAC"));

        map.put(SapCapability.ORDER_CAPABILITY, List.of(
                "Order Creation", "Order History", "Order Status", "Account Dashboard"));

        map.put(SapCapability.CUSTOMER_CAPABILITY, List.of(
                "Customer Registration", "Customer Profile Update", "Address Book", "Login"));

        map.put(SapCapability.PRODUCT_CAPABILITY, List.of(
                "PDP Rendering", "Catalog Sync", "Product Search", "Category Navigation"));

        map.put(SapCapability.PRICING_CAPABILITY, List.of(
                "Price Display", "Cart Totals", "Tax Calculation"));

        map.put(SapCapability.PROMOTION_CAPABILITY, List.of(
                "Coupon Flow", "Promotion Application", "Cart Totals"));

        map.put(SapCapability.INVENTORY_CAPABILITY, List.of(
                "Stock Availability", "Out of Stock Handling", "Stock Reservation"));

        map.put(SapCapability.DELIVERY_CAPABILITY, List.of(
                "Delivery Mode Selection", "Delivery Cost Calculation", "Fulfilment Handoff"));

        map.put(SapCapability.DATA_IMPORT, List.of(
                "Data Import Verification", "Migration Integrity", "Affected Entity Spot Check"));

        map.put(SapCapability.CONFIGURATION, List.of(
                "Environment Smoke Test", "Configuration-Dependent Flows"));

        map.put(SapCapability.SPRING_CONFIGURATION, List.of(
                "Application Startup", "Bean Wiring Smoke Test"));

        map.put(SapCapability.BACKGROUND_PROCESSING, List.of(
                "Cronjob Execution", "Job Scheduling", "Cleanup Verification"));

        map.put(SapCapability.INTEGRATION, List.of(
                "Integration Contract Tests", "Downstream Consumer Tests", "Error Handling"));

        map.put(SapCapability.DATA_ACCESS, List.of(
                "FlexibleSearch Query Verification", "Query Performance"));

        map.put(SapCapability.DATA_TRANSFORMATION, List.of(
                "DTO Payload Verification", "API Response Shape"));

        map.put(SapCapability.API_LAYER, List.of(
                "Endpoint Contract Tests", "Request Validation"));

        map.put(SapCapability.BUSINESS_LOGIC, List.of(
                "Affected Flow Regression"));

        map.put(SapCapability.BUILD_CONFIGURATION, List.of(
                "Build Verification", "Dependency Smoke Test"));

        return Map.copyOf(map);
    }
}
