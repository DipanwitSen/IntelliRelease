package com.gyansys.intellirelease.domain.impact;

import com.gyansys.intellirelease.model.enums.ConfidenceLevel;
import com.gyansys.intellirelease.model.enums.SapCapability;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The SAP Commerce dependency map: which capabilities are reachable from which,
 * and <em>why</em>.
 *
 * <p>Every edge carries its own reason, because an impact list without reasons
 * is a guess with extra steps. The reasons are what appear verbatim in the QA
 * note and in the "how do you know?" drilldown.
 *
 * <p>Curated Hybris knowledge, same discipline as
 * {@link com.gyansys.intellirelease.domain.context.CapabilityLibrary}: written
 * by engineers, versioned, never inferred at runtime.
 */
@Component
public class CapabilityGraph {

    public static final String GRAPH_VERSION = "capability-graph-2026.1";

    /** A directed dependency edge with the reason it exists. */
    public record Edge(SapCapability target, String reason, ConfidenceLevel confidence) {
    }

    private final Map<SapCapability, List<Edge>> edges;

    public CapabilityGraph() {
        this.edges = buildEdges();
    }

    /** Capabilities reachable in one hop. Never transitive — see the analyzer. */
    public List<Edge> neighboursOf(SapCapability capability) {
        return edges.getOrDefault(capability, List.of());
    }

    private static Map<SapCapability, List<Edge>> buildEdges() {
        Map<SapCapability, List<Edge>> map = new EnumMap<>(SapCapability.class);

        // A type system change is the widest blast radius in Hybris.
        put(map, SapCapability.TYPE_SYSTEM,
                edge(SapCapability.SEARCH, "Indexed attributes may change — a Solr reindex may be required", ConfidenceLevel.MEDIUM),
                edge(SapCapability.DATA_ACCESS, "FlexibleSearch queries referencing changed types may break", ConfidenceLevel.MEDIUM),
                edge(SapCapability.PRODUCT_CAPABILITY, "Generated product models and DTOs are regenerated", ConfidenceLevel.LOW),
                edge(SapCapability.CUSTOMER_CAPABILITY, "Generated customer models and DTOs are regenerated", ConfidenceLevel.LOW));

        put(map, SapCapability.CHECKOUT_CAPABILITY,
                edge(SapCapability.CART_CAPABILITY, "Checkout reads and mutates the cart", ConfidenceLevel.HIGH),
                edge(SapCapability.ORDER_CAPABILITY, "Checkout completion creates the order", ConfidenceLevel.HIGH),
                edge(SapCapability.PAYMENT, "Checkout invokes payment authorisation", ConfidenceLevel.HIGH),
                edge(SapCapability.PROMOTION_CAPABILITY, "Coupons and promotions are evaluated during checkout", ConfidenceLevel.MEDIUM),
                edge(SapCapability.INVENTORY_CAPABILITY, "Stock is reserved at checkout", ConfidenceLevel.MEDIUM),
                edge(SapCapability.DELIVERY_CAPABILITY, "Delivery mode is selected during checkout", ConfidenceLevel.MEDIUM));

        put(map, SapCapability.CART_CAPABILITY,
                edge(SapCapability.CHECKOUT_CAPABILITY, "Checkout consumes cart state", ConfidenceLevel.HIGH),
                edge(SapCapability.PROMOTION_CAPABILITY, "Cart totals include promotion and coupon calculation", ConfidenceLevel.MEDIUM),
                edge(SapCapability.PRICING_CAPABILITY, "Cart totals are recalculated from pricing", ConfidenceLevel.MEDIUM),
                edge(SapCapability.CUSTOMER_CAPABILITY, "Cart merge on login couples cart to customer session", ConfidenceLevel.MEDIUM));

        put(map, SapCapability.PAYMENT,
                edge(SapCapability.CHECKOUT_CAPABILITY, "Payment failures surface as checkout failures", ConfidenceLevel.HIGH),
                edge(SapCapability.ORDER_CAPABILITY, "Order placement depends on payment authorisation", ConfidenceLevel.HIGH),
                edge(SapCapability.CART_CAPABILITY, "Failed payment returns the shopper to the cart", ConfidenceLevel.MEDIUM));

        put(map, SapCapability.SEARCH_CONFIGURATION,
                edge(SapCapability.SEARCH, "Index and dictionary configuration drives query results", ConfidenceLevel.HIGH),
                edge(SapCapability.PRODUCT_CAPABILITY, "Product listing pages are rendered from search results", ConfidenceLevel.MEDIUM),
                edge(SapCapability.CMS_CAPABILITY, "Category and landing pages embed search-driven components", ConfidenceLevel.LOW));

        put(map, SapCapability.SEARCH,
                edge(SapCapability.PRODUCT_CAPABILITY, "Product discovery depends on search", ConfidenceLevel.MEDIUM),
                edge(SapCapability.CMS_CAPABILITY, "Search-backed content components may render differently", ConfidenceLevel.LOW));

        put(map, SapCapability.OCC_API,
                edge(SapCapability.CHECKOUT_CAPABILITY, "Spartacus drives checkout entirely through OCC", ConfidenceLevel.HIGH),
                edge(SapCapability.CART_CAPABILITY, "Cart operations are OCC endpoints", ConfidenceLevel.HIGH),
                edge(SapCapability.PRODUCT_CAPABILITY, "Product data reaches the storefront through OCC", ConfidenceLevel.MEDIUM),
                edge(SapCapability.INTEGRATION, "Mobile and third-party consumers bind to the OCC contract", ConfidenceLevel.MEDIUM));

        put(map, SapCapability.SECURITY,
                edge(SapCapability.CUSTOMER_CAPABILITY, "Authentication governs customer account access", ConfidenceLevel.HIGH),
                edge(SapCapability.CART_CAPABILITY, "Session lifetime determines cart persistence and abandonment", ConfidenceLevel.MEDIUM),
                edge(SapCapability.CHECKOUT_CAPABILITY, "Session expiry mid-checkout drops the shopper", ConfidenceLevel.MEDIUM),
                edge(SapCapability.OCC_API, "Token handling affects every API consumer", ConfidenceLevel.MEDIUM));

        put(map, SapCapability.CONFIGURATION,
                edge(SapCapability.SECURITY, "Session and auth behaviour is property-driven", ConfidenceLevel.MEDIUM),
                edge(SapCapability.PAYMENT, "Gateway timeouts and endpoints are property-driven", ConfidenceLevel.MEDIUM),
                edge(SapCapability.SEARCH, "Solr endpoints and index settings are property-driven", ConfidenceLevel.LOW),
                edge(SapCapability.BACKGROUND_PROCESSING, "Cronjob schedules and batch sizes are property-driven", ConfidenceLevel.LOW));

        put(map, SapCapability.DATA_IMPORT,
                edge(SapCapability.PRODUCT_CAPABILITY, "Catalog rows may be created or updated", ConfidenceLevel.MEDIUM),
                edge(SapCapability.CUSTOMER_CAPABILITY, "Customer rows may be created or updated", ConfidenceLevel.MEDIUM),
                edge(SapCapability.CMS_CAPABILITY, "Content rows may be created or updated", ConfidenceLevel.LOW),
                edge(SapCapability.SEARCH, "Imported data changes what the index contains", ConfidenceLevel.MEDIUM));

        put(map, SapCapability.SPRING_CONFIGURATION,
                edge(SapCapability.BUSINESS_LOGIC, "Bean replacement silently changes runtime behaviour", ConfidenceLevel.MEDIUM),
                edge(SapCapability.BACKGROUND_PROCESSING, "Cronjob beans are wired here", ConfidenceLevel.LOW),
                edge(SapCapability.INTEGRATION, "Integration adapters are wired here", ConfidenceLevel.LOW));

        put(map, SapCapability.BACKGROUND_PROCESSING,
                edge(SapCapability.SEARCH, "Index cronjobs keep Solr current", ConfidenceLevel.MEDIUM),
                edge(SapCapability.ORDER_CAPABILITY, "Order processing jobs advance order state", ConfidenceLevel.MEDIUM),
                edge(SapCapability.CUSTOMER_CAPABILITY, "Cleanup jobs age out customer-linked records", ConfidenceLevel.LOW));

        put(map, SapCapability.CMS_CAPABILITY,
                edge(SapCapability.PRODUCT_CAPABILITY, "Content slots surface product components", ConfidenceLevel.LOW),
                edge(SapCapability.SEARCH, "Content-driven navigation feeds search entry points", ConfidenceLevel.LOW));

        put(map, SapCapability.INTEGRATION,
                edge(SapCapability.ORDER_CAPABILITY, "Orders are replicated to ERP", ConfidenceLevel.MEDIUM),
                edge(SapCapability.PRODUCT_CAPABILITY, "Product master data arrives from upstream systems", ConfidenceLevel.MEDIUM),
                edge(SapCapability.PRICING_CAPABILITY, "Prices are synchronised from ERP", ConfidenceLevel.MEDIUM));

        put(map, SapCapability.ORDER_CAPABILITY,
                edge(SapCapability.CUSTOMER_CAPABILITY, "Order history renders in the account area", ConfidenceLevel.MEDIUM),
                edge(SapCapability.PAYMENT, "Order state transitions follow payment events", ConfidenceLevel.MEDIUM),
                edge(SapCapability.DELIVERY_CAPABILITY, "Fulfilment is driven from the order", ConfidenceLevel.MEDIUM));

        put(map, SapCapability.PRODUCT_CAPABILITY,
                edge(SapCapability.SEARCH, "Product attribute changes affect indexing", ConfidenceLevel.MEDIUM),
                edge(SapCapability.PRICING_CAPABILITY, "Prices are attached to products", ConfidenceLevel.LOW),
                edge(SapCapability.CMS_CAPABILITY, "Product components render on content pages", ConfidenceLevel.LOW));

        put(map, SapCapability.CUSTOMER_CAPABILITY,
                edge(SapCapability.ORDER_CAPABILITY, "Orders are owned by a customer", ConfidenceLevel.MEDIUM),
                edge(SapCapability.CART_CAPABILITY, "Saved carts are customer-scoped", ConfidenceLevel.MEDIUM));

        put(map, SapCapability.PRICING_CAPABILITY,
                edge(SapCapability.CART_CAPABILITY, "Cart totals derive from pricing", ConfidenceLevel.MEDIUM),
                edge(SapCapability.CHECKOUT_CAPABILITY, "Order totals are fixed at checkout", ConfidenceLevel.MEDIUM));

        put(map, SapCapability.DATA_ACCESS,
                edge(SapCapability.TYPE_SYSTEM, "DAOs are coupled to the type system they query", ConfidenceLevel.LOW));

        put(map, SapCapability.DATA_TRANSFORMATION,
                edge(SapCapability.OCC_API, "Converters shape the payloads OCC returns", ConfidenceLevel.MEDIUM));

        return map;
    }

    private static void put(Map<SapCapability, List<Edge>> map, SapCapability from, Edge... targets) {
        map.put(from, List.copyOf(new ArrayList<>(List.of(targets))));
    }

    private static Edge edge(SapCapability target, String reason, ConfidenceLevel confidence) {
        return new Edge(target, reason, confidence);
    }
}
