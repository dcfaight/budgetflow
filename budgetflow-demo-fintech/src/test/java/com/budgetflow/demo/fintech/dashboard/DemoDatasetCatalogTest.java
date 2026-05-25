package com.budgetflow.demo.fintech.dashboard;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoDatasetCatalogTest {

    @Test
    void defaultSeedDatasetLoadsExpectedDemoRecords() {
        DemoDatasetCatalog catalog = new DemoDatasetCatalog(
            new DefaultResourceLoader(),
            JsonMapper.builder().findAndAddModules().build(),
            "seed/default"
        );

        assertEquals("seed/default", catalog.selectedDatasetId());
        assertEquals(BigDecimal.valueOf(1024.56), catalog.resolveBalance("acc-123").available());
        assertEquals("Coffee Shop", catalog.resolveTransactions("acc-123").get(0).merchant());
    }

    @Test
    void scenarioDatasetIncludesMetadataAndCoreEntities() {
        DemoDatasetCatalog catalog = new DemoDatasetCatalog(
            new DefaultResourceLoader(),
            JsonMapper.builder().findAndAddModules().build(),
            "seed/default"
        );

        DemoDatasetCatalog.DatasetPack scenario = catalog.loadDataset("scenarios/overspending-user");
        assertEquals("overspending-user", scenario.scenarioMetadata().scenarioId());
        assertTrue(scenario.scenarioMetadata().expectedEvaluatorBehavior().contains("budget overruns"));
        assertFalse(scenario.customers().isEmpty());
        assertFalse(scenario.accounts().isEmpty());
        assertFalse(scenario.transactions().isEmpty());
        assertFalse(scenario.budgets().isEmpty());
    }

    @Test
    void rejectsPathTraversalDatasetIds() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new DemoDatasetCatalog(
                new DefaultResourceLoader(),
                JsonMapper.builder().findAndAddModules().build(),
                "../seed/default"
            )
        );
    }
}
