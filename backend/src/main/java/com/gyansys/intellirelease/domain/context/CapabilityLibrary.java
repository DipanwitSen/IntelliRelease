package com.gyansys.intellirelease.domain.context;

import com.gyansys.intellirelease.domain.context.knowledge.ArtifactCapabilityMapping;
import com.gyansys.intellirelease.domain.context.knowledge.SapCommerceKnowledgeBase;
import com.gyansys.intellirelease.model.enums.SapCapability;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The curated SAP Commerce knowledge library.
 *
 * <p>This is the difference between "we understand your platform" and "we ran
 * a generic tool over it". GitHub can tell you {@code items.xml} changed;
 * only this library knows that means type system, therefore DB schema,
 * therefore generated models, therefore a possible Solr reindex.
 *
 * <p>The classification rules themselves live in {@code sap_context.json}
 * ({@link SapCommerceKnowledgeBase} loads and matches it) — a ~65-artifact-
 * type SAP Commerce/Hybris knowledge base, hand-authored and versioned, no
 * different in kind from the Java rule list this class used to hold directly.
 * Moving it to data means the domain expert who wrote it can revise it
 * without touching Java, and the engine stays exactly as auditable: every
 * classification traces back to one named pattern in one versioned file.
 *
 * <p>The business-domain keyword map below is unrelated and stays hand-coded
 * here — it is a small, stable heuristic ("{@code CheckoutFacade} implies the
 * Checkout business capability regardless of its architectural layer"), not
 * part of the artifact-type taxonomy the knowledge base owns.
 */
@Component
public class CapabilityLibrary {

    private final SapCommerceKnowledgeBase knowledgeBase;
    private final Map<String, SapCapability> businessDomainKeywords;

    public CapabilityLibrary(SapCommerceKnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
        this.businessDomainKeywords = buildBusinessDomainKeywords();
    }

    /** The knowledge base's own version string, carried onto every {@link ContextResult}. */
    public String libraryVersion() {
        return "sap-context-kb-" + knowledgeBase.version();
    }

    /**
     * Classifies a path against the knowledge base. Empty means no curated
     * pattern matched — the engine reports UNCLASSIFIED rather than guessing.
     */
    public Optional<SapCommerceKnowledgeBase.Match> classify(PathFacts facts) {
        return knowledgeBase.classify(facts.path());
    }

    /** The legacy capability bucket a matched artifact type maps onto — see {@link ArtifactCapabilityMapping}. */
    public SapCapability legacyCapability(String artifactType) {
        return ArtifactCapabilityMapping.toLegacyCapability(artifactType);
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
}
