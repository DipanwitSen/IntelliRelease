package com.gyansys.intellirelease.model.enums;

/**
 * SAP Commerce (Hybris) capabilities the Context Engine can recognise.
 *
 * <p>This vocabulary is curated Hybris domain knowledge, not model inference.
 * Everything downstream — Impact, Regression, Risk, Drift, Readiness — is keyed
 * on these values, so the platform is only ever as good as this enum plus
 * {@link com.gyansys.intellirelease.domain.context.CapabilityLibrary}.
 *
 * <p>{@link #UNCLASSIFIED} is a first-class outcome, not a failure. When a file
 * matches no known pattern the engine says so and downstream engines treat it
 * conservatively — never optimistically.
 */
public enum SapCapability {

    // --- Structural / platform-level -------------------------------------
    TYPE_SYSTEM("Type System Change", "items.xml type definitions, DB schema, generated models"),
    SEARCH_CONFIGURATION("Search Configuration", "Solr cores, indexed properties, synonyms"),
    DATA_IMPORT("Data Import", "Impex data load"),
    SPRING_CONFIGURATION("Bean Wiring", "Spring bean definitions and dependency graph"),
    CONFIGURATION("Configuration", "Environment behaviour: timeouts, caches, feature flags"),
    BUILD_CONFIGURATION("Build Configuration", "Maven/Gradle build and dependency set"),
    BACKGROUND_PROCESSING("Background Processing", "Cronjobs: cleanup, indexing, synchronisation"),

    // --- Architectural layers ---------------------------------------------
    BUSINESS_LOGIC("Business Logic", "Facades, services and strategies"),
    API_LAYER("API Layer", "Controllers and request handling"),
    DATA_ACCESS("Data Access", "DAOs and FlexibleSearch queries"),
    DATA_TRANSFORMATION("Data Transformation", "Converters and populators"),

    // --- Business capabilities --------------------------------------------
    CHECKOUT_CAPABILITY("Checkout Capability", "Checkout flow across all channels"),
    CART_CAPABILITY("Cart Capability", "Cart lifecycle and persistence"),
    ORDER_CAPABILITY("Order Capability", "Order placement, history and status"),
    PRODUCT_CAPABILITY("Product Capability", "Product model and catalog"),
    CUSTOMER_CAPABILITY("Customer Capability", "Customer accounts and profiles"),
    PRICING_CAPABILITY("Pricing Capability", "Prices, taxes and pricing rules"),
    PROMOTION_CAPABILITY("Promotion Capability", "Promotions, coupons and rule engine"),
    INVENTORY_CAPABILITY("Inventory Capability", "Stock levels and availability"),
    DELIVERY_CAPABILITY("Delivery Capability", "Delivery modes and fulfilment"),
    PAYMENT("Payment", "Payment gateway, retries and refunds"),
    SEARCH("Search", "Solr queries and search services"),
    CMS_CAPABILITY("CMS Capability", "Homepage, landing pages, content slots, components"),
    OCC_API("Storefront API (OCC)", "Spartacus and mobile app consumers"),
    SECURITY("Security", "Authentication, authorization, session management"),
    INTEGRATION("Integration", "SAP CPI, ERP and third-party interfaces"),

    // --- Signals -----------------------------------------------------------
    TEST("Test", "Test coverage signal"),

    /** No known pattern matched. The engine never guesses. */
    UNCLASSIFIED("Unclassified", "No curated pattern matched this file");

    private final String displayName;
    private final String description;

    SapCapability(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /** True for capabilities a business stakeholder would recognise by name. */
    public boolean isBusinessCapability() {
        return switch (this) {
            case CHECKOUT_CAPABILITY, CART_CAPABILITY, ORDER_CAPABILITY, PRODUCT_CAPABILITY,
                 CUSTOMER_CAPABILITY, PRICING_CAPABILITY, PROMOTION_CAPABILITY,
                 INVENTORY_CAPABILITY, DELIVERY_CAPABILITY, PAYMENT, SEARCH,
                 CMS_CAPABILITY, OCC_API -> true;
            default -> false;
        };
    }
}
