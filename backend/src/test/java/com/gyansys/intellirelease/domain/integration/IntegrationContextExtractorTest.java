package com.gyansys.intellirelease.domain.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gyansys.intellirelease.domain.integration.IntegrationModel.IntegrationContext;
import com.gyansys.intellirelease.model.enums.ProvenanceClass;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two behaviours the module's credibility rests on: it names real
 * impact when there is some, and it says "nothing" when there is not.
 *
 * <p>Over-reporting is the worse failure. A release manager who sees the
 * integration panel light up on every storefront CSS tweak stops reading it,
 * and then misses the one change that genuinely moves a payload.
 */
class IntegrationContextExtractorTest {

    private static IntegrationContextExtractor extractor;
    private static IntegrationCatalog catalog;

    @BeforeAll
    static void loadCatalogue() {
        catalog = new IntegrationCatalog(new ObjectMapper());
        extractor = new IntegrationContextExtractor(catalog);
    }

    @Test
    @DisplayName("a storefront-only change touches no integration surface")
    void storefrontChangeTouchesNothing() {
        IntegrationContext context = extractor.extract(List.of(
                "frontend/src/styles/theme.scss",
                "frontend/src/app/components/banner.component.ts",
                "README.md"));

        assertThat(context.touched())
                .as("reporting impact that does not exist trains people to ignore the panel")
                .isFalse();
        assertThat(context.impactedInterfaces()).isEmpty();
    }

    @Test
    @DisplayName("an order converter change names the outbound order interface")
    void orderConverterNamesOrderInterface() {
        IntegrationContext context = extractor.extract(List.of(
                "core/src/com/example/order/converters/OrderExportConverter.java"));

        assertThat(context.touched()).isTrue();
        assertThat(context.impactedInterfaces())
                .extracting(IntegrationModel.ImpactedInterface::interfaceId)
                .contains("out-order-create");
        assertThat(context.provenance()).isEqualTo(ProvenanceClass.DERIVED_FACT);
    }

    @Test
    @DisplayName("every finding carries the evidence that produced it")
    void findingsCarryEvidence() {
        IntegrationContext context = extractor.extract(List.of(
                "core/src/com/example/order/converters/OrderExportConverter.java"));

        assertThat(context.evidence()).isNotEmpty();
        assertThat(context.evidence().get(0))
                .as("evidence must name the file so a reviewer can check the claim")
                .contains("OrderExportConverter.java");
        assertThat(context.impactedInterfaces().get(0).reason()).isNotBlank();
    }

    @Test
    @DisplayName("a type-system change reports critical severity")
    void itemsXmlIsCritical() {
        IntegrationContext context = extractor.extract(List.of(
                "core/resources/example-items.xml"));

        assertThat(context.touched()).isTrue();
        assertThat(context.impactedInterfaces())
                .as("the type system is the floor of the platform — its blast radius is the widest there is")
                .anyMatch(item -> "CRITICAL".equals(item.severity()));
    }

    @Test
    @DisplayName("a price ImpEx change names the file-based price interface and its mapping")
    void priceImpexNamesFileInterface() {
        IntegrationContext context = extractor.extract(List.of(
                "core/resources/impex/price-import.impex"));

        assertThat(context.touched()).isTrue();
        assertThat(context.impactedInterfaces())
                .extracting(IntegrationModel.ImpactedInterface::interfaceId)
                .contains("in-price");
        assertThat(context.impactedMappings())
                .extracting(IntegrationModel.MappingRef::id)
                .contains("map-price-in");
    }

    @Test
    @DisplayName("a file-based interface reports no middleware flow")
    void fileBasedHasNoMiddleware() {
        IntegrationContext context = extractor.extract(List.of(
                "core/resources/impex/price-import.impex"));

        assertThat(context.impactedMiddlewareFlows())
                .as("a CSV drop has no middleware hop, and the model must not invent one")
                .isEmpty();
        assertThat(context.detectedTopologies()).contains("FILE_BASED");
    }

    @Test
    @DisplayName("the strongest severity wins when one interface is hit twice")
    void strongestSeverityWins() {
        IntegrationContext context = extractor.extract(List.of(
                "core/resources/example-items.xml",                       // CRITICAL
                "core/src/com/example/order/converters/OrderConverter.java"));  // HIGH

        assertThat(context.impactedInterfaces())
                .filteredOn(item -> "out-order-create".equals(item.interfaceId()))
                .allMatch(item -> "CRITICAL".equals(item.severity()));
    }

    @Test
    @DisplayName("a WSDL change is recognised as an API contract change")
    void wsdlTouchesApiContract() {
        assertThat(catalog.apisTouchedBy(List.of("integration/contracts/SalesOrder.wsdl")))
                .contains("api-order-inbound");
    }

    @Test
    @DisplayName("an empty or null change set is handled without throwing")
    void handlesEmptyInput() {
        assertThat(extractor.extract(List.of()).touched()).isFalse();
        assertThat(extractor.extract(null).touched()).isFalse();
    }

    @Test
    @DisplayName("only topologies actually in use are reported as active")
    void activeTopologiesReflectTheCatalogue() {
        List<String> active = catalog.activeTopologies().stream()
                .map(IntegrationModel.TopologyProfile::id)
                .toList();

        assertThat(active).isNotEmpty();
        assertThat(catalog.topologies().size())
                .as("the catalogue may define topologies this landscape does not use")
                .isGreaterThanOrEqualTo(active.size());
    }
}
