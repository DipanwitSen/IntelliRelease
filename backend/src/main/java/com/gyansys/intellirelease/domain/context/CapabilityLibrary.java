package com.gyansys.intellirelease.domain.context;

import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.SapCapability;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The curated SAP Commerce knowledge library.
 *
 * <p>This file is the difference between "we understand your platform" and "we
 * ran a generic tool over it". GitHub can tell you {@code items.xml} changed;
 * only this library knows that means type system, therefore DB schema,
 * therefore generated models, therefore a possible Solr reindex.
 *
 * <p>It is versioned domain knowledge, deliberately hand-written and readable.
 * No model produced any line of it, and none of it is inferred at runtime.
 *
 * <p><strong>Ordering is semantics.</strong> Rules are evaluated top to bottom
 * and the first match wins, so specific patterns precede general ones.
 */
@Component
public class CapabilityLibrary {

    /** Bump when rules change so past classifications stay explainable. */
    public static final String LIBRARY_VERSION = "hybris-knowledge-2026.1";

    private final List<CapabilityRule> rules;
    private final Map<String, SapCapability> businessDomainKeywords;

    public CapabilityLibrary() {
        this.rules = buildRules();
        this.businessDomainKeywords = buildBusinessDomainKeywords();
    }

    public List<CapabilityRule> rules() {
        return rules;
    }

    /**
     * First matching rule, or empty when nothing in the library applies.
     * Empty is a legitimate answer: the engine reports UNCLASSIFIED rather than
     * inventing a meaning.
     */
    public java.util.Optional<CapabilityRule> firstMatch(PathFacts facts) {
        return rules.stream().filter(rule -> rule.matches(facts)).findFirst();
    }

    /**
     * Business capability implied by the class name, independent of its
     * architectural layer. {@code OrderHistoryService.java} is business logic
     * <em>and</em> the Order capability; QA cares about the second one.
     */
    public SapCapability detectBusinessDomain(PathFacts facts) {
        for (Map.Entry<String, SapCapability> entry : businessDomainKeywords.entrySet()) {
            if (facts.fileNameContains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // The library
    // ------------------------------------------------------------------

    private static List<CapabilityRule> buildRules() {
        return List.of(

                // === Most specific: named Hybris facades ==================
                rule("checkout-facade",
                        f -> f.fileNameContains("checkoutfacade") || f.fileNameContains("checkoutservice"),
                        SapCapability.CHECKOUT_CAPABILITY,
                        List.of("Cart", "Order", "Payment", "Coupons", "Inventory"),
                        ConfidenceLevel.HIGH,
                        "File name matches the checkout facade/service pattern"),

                rule("cart-facade",
                        f -> f.fileNameContains("cartfacade") || f.fileNameContains("cartservice"),
                        SapCapability.CART_CAPABILITY,
                        List.of("Guest Checkout", "Registered Checkout", "Cart Merge", "Saved Cart", "Coupons"),
                        ConfidenceLevel.HIGH,
                        "File name matches the cart facade/service pattern"),

                // === Type system: the highest-consequence change in Hybris ==
                rule("type-system-items-xml",
                        f -> f.fileNameIs("items.xml") || f.fileNameEndsWith("-items.xml"),
                        SapCapability.TYPE_SYSTEM,
                        List.of("DB schema affected", "Generated models", "Generated DTOs",
                                "Generated services", "System update required",
                                "Possible Solr reindex", "FlexibleSearch impact"),
                        ConfidenceLevel.HIGH,
                        "items.xml defines the Hybris type system; changes propagate to schema and generated code"),

                // === Search configuration ==================================
                rule("search-config-solr-impex",
                        f -> f.extensionIs("impex") && (f.fileNameContains("solr") || f.inDirectory("solr")),
                        SapCapability.SEARCH_CONFIGURATION,
                        List.of("Solr", "PLP", "Search API", "Facets", "Autosuggest"),
                        ConfidenceLevel.HIGH,
                        "Impex under a Solr path configures search indexing and facets"),

                rule("search-config-synonyms",
                        f -> f.fileNameContains("synonyms") || f.fileNameContains("stopwords")
                                || f.fileNameIs("schema.xml") || f.fileNameIs("solrconfig.xml"),
                        SapCapability.SEARCH_CONFIGURATION,
                        List.of("Search relevance", "Autosuggest", "Facets", "Category navigation"),
                        ConfidenceLevel.HIGH,
                        "Solr dictionary or core configuration alters search behaviour for affected terms"),

                // === Data import ===========================================
                rule("data-import-impex",
                        f -> f.extensionIs("impex"),
                        SapCapability.DATA_IMPORT,
                        List.of("Depends on content: catalog, users, media or content",
                                "Data state change on target environment",
                                "Import order sensitivity"),
                        ConfidenceLevel.HIGH,
                        "Impex performs a data import; consequences depend on the rows it carries"),

                // === Public API surface ====================================
                rule("occ-api",
                        f -> f.fileNameContains("occcontroller") || f.inDirectory("occ")
                                || f.pathContains("commercewebservices"),
                        SapCapability.OCC_API,
                        List.of("Spartacus storefront impact", "Mobile app impact",
                                "Downstream consumers", "API contract"),
                        ConfidenceLevel.HIGH,
                        "OCC controllers form the public storefront API consumed outside the platform"),

                rule("api-contract-spec",
                        f -> f.fileNameContains("openapi") || f.fileNameContains("swagger"),
                        SapCapability.OCC_API,
                        List.of("API contract", "Consumer integration", "Client SDK regeneration"),
                        ConfidenceLevel.HIGH,
                        "An OpenAPI specification is the published contract for API consumers"),

                // === Background processing =================================
                rule("cronjob",
                        f -> f.fileNameContains("cronjob") || f.fileNameContains("croncob")
                                || f.inDirectory("cronjob") || f.inDirectory("cronjobs"),
                        SapCapability.BACKGROUND_PROCESSING,
                        List.of("Cleanup jobs", "Index jobs", "Synchronisation", "Scheduled tasks"),
                        ConfidenceLevel.HIGH,
                        "Cronjob classes drive scheduled background processing"),

                // === Security ==============================================
                rule("security",
                        f -> f.fileNameContains("security") || f.fileNameContains("authentication")
                                || f.fileNameContains("authorization") || f.fileNameContains("oauth")
                                || f.fileNameContains("jwt"),
                        SapCapability.SECURITY,
                        List.of("Authentication", "Authorization", "Session management", "Access control"),
                        ConfidenceLevel.HIGH,
                        "Security and authentication components govern who may do what"),

                // === Payment ===============================================
                rule("payment",
                        f -> f.fileNameContains("payment") || f.fileNameContains("paymentgateway"),
                        SapCapability.PAYMENT,
                        List.of("Payment gateway", "Retry behaviour", "Refunds", "Order placement"),
                        ConfidenceLevel.HIGH,
                        "Payment components are business-critical and gateway-facing"),

                // === CMS ===================================================
                rule("cms",
                        f -> f.fileNameContains("cms") || f.inDirectory("cms"),
                        SapCapability.CMS_CAPABILITY,
                        List.of("Homepage", "Landing pages", "Content slots", "Components"),
                        ConfidenceLevel.HIGH,
                        "CMS components control rendered page content and layout"),

                // === Integration ===========================================
                rule("integration",
                        f -> f.fileNameContains("integration") || f.inDirectory("integration")
                                || f.fileNameContains("outbound") || f.fileNameContains("inbound"),
                        SapCapability.INTEGRATION,
                        List.of("SAP CPI", "ERP", "Third-party interfaces", "Contract compatibility"),
                        ConfidenceLevel.MEDIUM,
                        "Integration components exchange data with systems outside SAP Commerce"),

                // === Search services =======================================
                rule("search-service",
                        f -> f.fileNameContains("solr") || f.fileNameContains("search"),
                        SapCapability.SEARCH,
                        List.of("Solr queries", "Search service", "PLP", "Autosuggest"),
                        ConfidenceLevel.MEDIUM,
                        "Search service components affect query construction and results"),

                // === Tests: a coverage signal, not a capability =============
                rule("test",
                        f -> f.fileNameEndsWith("test.java") || f.fileNameEndsWith("tests.java")
                                || f.fileNameEndsWith("it.java") || f.fileNameEndsWith("spec.ts")
                                || f.inDirectory("test") || f.inDirectory("tests"),
                        SapCapability.TEST,
                        List.of("Test coverage signal"),
                        ConfidenceLevel.HIGH,
                        "Test sources accompany the change; recorded as positive test evidence"),

                // === Configuration =========================================
                rule("spring-configuration",
                        f -> f.extensionIs("xml")
                                && (f.fileNameStartsWith("spring-") || f.fileNameEndsWith("-spring.xml")
                                    || f.fileNameContains("spring")),
                        SapCapability.SPRING_CONFIGURATION,
                        List.of("Bean wiring", "Dependency graph impact", "Potential type mismatch at startup"),
                        ConfidenceLevel.HIGH,
                        "Spring XML alters bean wiring and the runtime dependency graph"),

                rule("environment-configuration",
                        f -> f.fileNameIs("local.properties")
                                || f.fileNameStartsWith("application")
                                   && f.extensionIsAnyOf("properties", "yml", "yaml")
                                || f.fileNameEndsWith(".properties"),
                        SapCapability.CONFIGURATION,
                        List.of("Environment behaviour", "Caches", "Timeouts", "Feature flags",
                                "Differs per environment — drift candidate"),
                        ConfidenceLevel.HIGH,
                        "Property and YAML configuration changes environment behaviour without changing code"),

                rule("build-configuration",
                        f -> f.fileNameIs("pom.xml") || f.fileNameIs("build.gradle")
                                || f.fileNameIs("build.gradle.kts") || f.fileNameIs("external-dependencies.xml"),
                        SapCapability.BUILD_CONFIGURATION,
                        List.of("Dependency set", "Build reproducibility", "Transitive version changes"),
                        ConfidenceLevel.HIGH,
                        "Build descriptors define the dependency set compiled into the platform"),

                // === Architectural layers: general fallbacks ================
                rule("api-layer-controller",
                        f -> f.fileNameEndsWith("controller.java"),
                        SapCapability.API_LAYER,
                        List.of("Request handling", "Endpoint contract", "Consumer impact"),
                        ConfidenceLevel.HIGH,
                        "Controllers expose request handling; the business capability is detected separately"),

                rule("business-logic-facade",
                        f -> f.fileNameEndsWith("facade.java") || f.fileNameContains("facadeimpl"),
                        SapCapability.BUSINESS_LOGIC,
                        List.of("Orchestration across services", "Storefront-facing behaviour"),
                        ConfidenceLevel.HIGH,
                        "Facades orchestrate services for a storefront-facing use case"),

                rule("business-logic-strategy",
                        f -> f.fileNameEndsWith("strategy.java") || f.fileNameContains("strategyimpl"),
                        SapCapability.BUSINESS_LOGIC,
                        List.of("Pluggable business rule", "Behaviour varies by configuration"),
                        ConfidenceLevel.HIGH,
                        "Strategies encapsulate swappable business rules"),

                rule("business-logic-service",
                        f -> f.fileNameEndsWith("service.java") || f.fileNameContains("serviceimpl"),
                        SapCapability.BUSINESS_LOGIC,
                        List.of("Core business behaviour", "Called by facades and cronjobs"),
                        ConfidenceLevel.HIGH,
                        "Services implement core business behaviour"),

                rule("data-access-dao",
                        f -> f.fileNameEndsWith("dao.java") || f.fileNameContains("daoimpl"),
                        SapCapability.DATA_ACCESS,
                        List.of("FlexibleSearch queries", "Query performance", "Type system coupling"),
                        ConfidenceLevel.HIGH,
                        "DAOs issue FlexibleSearch queries against the type system"),

                rule("data-transformation",
                        f -> f.fileNameEndsWith("converter.java") || f.fileNameEndsWith("populator.java"),
                        SapCapability.DATA_TRANSFORMATION,
                        List.of("DTO shape", "API response payload", "Storefront rendering"),
                        ConfidenceLevel.HIGH,
                        "Converters and populators shape the DTOs returned to callers")
        );
    }

    /**
     * Keyword to business capability. Ordered: the first keyword found in the
     * file name wins, so {@code Checkout} beats {@code Order} in
     * {@code CheckoutOrderFacade}.
     */
    private static Map<String, SapCapability> buildBusinessDomainKeywords() {
        Map<String, SapCapability> keywords = new LinkedHashMap<>();
        keywords.put("checkout", SapCapability.CHECKOUT_CAPABILITY);
        keywords.put("cart", SapCapability.CART_CAPABILITY);
        keywords.put("payment", SapCapability.PAYMENT);
        keywords.put("order", SapCapability.ORDER_CAPABILITY);
        keywords.put("promotion", SapCapability.PROMOTION_CAPABILITY);
        keywords.put("coupon", SapCapability.PROMOTION_CAPABILITY);
        keywords.put("voucher", SapCapability.PROMOTION_CAPABILITY);
        keywords.put("price", SapCapability.PRICING_CAPABILITY);
        keywords.put("pricing", SapCapability.PRICING_CAPABILITY);
        keywords.put("tax", SapCapability.PRICING_CAPABILITY);
        keywords.put("stock", SapCapability.INVENTORY_CAPABILITY);
        keywords.put("inventory", SapCapability.INVENTORY_CAPABILITY);
        keywords.put("delivery", SapCapability.DELIVERY_CAPABILITY);
        keywords.put("shipping", SapCapability.DELIVERY_CAPABILITY);
        keywords.put("fulfilment", SapCapability.DELIVERY_CAPABILITY);
        keywords.put("fulfillment", SapCapability.DELIVERY_CAPABILITY);
        keywords.put("customer", SapCapability.CUSTOMER_CAPABILITY);
        keywords.put("product", SapCapability.PRODUCT_CAPABILITY);
        keywords.put("catalog", SapCapability.PRODUCT_CAPABILITY);
        keywords.put("category", SapCapability.PRODUCT_CAPABILITY);
        keywords.put("search", SapCapability.SEARCH);
        keywords.put("solr", SapCapability.SEARCH);
        keywords.put("cms", SapCapability.CMS_CAPABILITY);
        return Map.copyOf(keywords);
    }

    private static CapabilityRule rule(String name, Predicate<PathFacts> predicate,
                                       SapCapability capability, List<String> consequences,
                                       ConfidenceLevel confidence, String evidence) {
        return new CapabilityRule(name, predicate, capability, consequences, confidence, evidence);
    }
}
