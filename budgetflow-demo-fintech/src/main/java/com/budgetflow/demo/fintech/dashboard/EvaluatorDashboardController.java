package com.budgetflow.demo.fintech.dashboard;

import com.budgetflow.core.policy.PlannerPolicyProfile;
import com.budgetflow.demo.fintech.benchmark.DashboardScenarioPack;
import com.budgetflow.demo.fintech.benchmark.PressureScenarios;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class EvaluatorDashboardController {
    private static final List<String> SUPPORTED_PACKS = List.of("default", "extended", "realism", "policy", "adoption");
    private final EvaluatorDashboardService evaluatorDashboardService;
    private final DemoDatasetCatalog demoDatasetCatalog;

    public EvaluatorDashboardController(
        EvaluatorDashboardService evaluatorDashboardService,
        DemoDatasetCatalog demoDatasetCatalog
    ) {
        this.evaluatorDashboardService = evaluatorDashboardService;
        this.demoDatasetCatalog = demoDatasetCatalog;
    }

    @GetMapping(value = "/dashboard/evaluator", produces = MediaType.TEXT_HTML_VALUE)
    public String evaluatorDashboard(
        @RequestParam(name = "pack", defaultValue = "default") String packName,
        @RequestParam(name = "scenario", required = false) String scenarioName,
        @RequestParam(name = "compareScenarios", required = false) String compareScenarios,
        @RequestParam(name = "profile", defaultValue = "balanced") String profileName,
        @RequestParam(name = "compareProfiles", defaultValue = "balanced,continuity,efficiency") String compareProfiles,
        @RequestParam(name = "dataset", required = false) String datasetId,
        @RequestParam(name = "compareDatasets", required = false) String compareDatasets,
        @RequestParam(name = "walkthroughStep", required = false) String walkthroughStep
    ) {
        String safePack = sanitizePack(packName);
        DashboardScenarioPack resolvedPack = PressureScenarios.packNamed(safePack);
        PlannerPolicyProfile safeProfile = sanitizeProfile(profileName);
        String safeScenario = sanitizeScenarioName(scenarioName, resolvedPack);
        String safeCompareScenarios = sanitizeCompareScenarios(compareScenarios, resolvedPack, safeScenario);
        String safeCompareProfiles = sanitizeCompareProfiles(compareProfiles, safeProfile);
        String safeDatasetId = sanitizeDatasetId(datasetId);
        String safeCompareDatasets = sanitizeCompareDatasets(compareDatasets, safeDatasetId);
        return evaluatorDashboardService.render(
            safePack,
            safeScenario,
            safeCompareScenarios,
            safeProfile.configName(),
            safeCompareProfiles,
            safeDatasetId,
            safeCompareDatasets,
            walkthroughStep
        );
    }

    private String sanitizePack(String packName) {
        return SUPPORTED_PACKS.contains(packName) ? packName : "default";
    }

    private PlannerPolicyProfile sanitizeProfile(String profileName) {
        try {
            return PlannerPolicyProfile.fromConfigName(profileName);
        } catch (IllegalArgumentException exception) {
            return PlannerPolicyProfile.BALANCED;
        }
    }

    private String sanitizeScenarioName(String scenarioName, DashboardScenarioPack pack) {
        if (scenarioName == null || scenarioName.isBlank()) {
            return null;
        }
        return pack.scenarios().stream()
            .map(scenario -> scenario.name())
            .filter(name -> name.equals(scenarioName))
            .findFirst()
            .orElse(null);
    }

    private String sanitizeCompareProfiles(String compareProfiles, PlannerPolicyProfile selectedProfile) {
        List<String> sanitized = Arrays.stream(compareProfiles.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .map(this::sanitizeProfile)
            .map(PlannerPolicyProfile::configName)
            .distinct()
            .collect(Collectors.toList());
        if (sanitized.isEmpty()) {
            sanitized = List.of("balanced", "continuity", "efficiency");
        }
        if (!sanitized.contains(selectedProfile.configName())) {
            sanitized = new java.util.ArrayList<>(sanitized);
            sanitized.add(selectedProfile.configName());
        }
        return String.join(",", sanitized);
    }

    private String sanitizeCompareScenarios(String compareScenarios, DashboardScenarioPack pack, String selectedScenario) {
        if (compareScenarios == null || compareScenarios.isBlank()) {
            return selectedScenario == null || selectedScenario.isBlank()
                ? ""
                : selectedScenario;
        }
        List<String> scenarioNames = pack.scenarios().stream()
            .map(scenario -> scenario.name())
            .collect(Collectors.toList());
        List<String> sanitized = Arrays.stream(compareScenarios.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .filter(scenarioNames::contains)
            .distinct()
            .limit(4)
            .collect(Collectors.toList());
        if (sanitized.isEmpty() && selectedScenario != null && !selectedScenario.isBlank()) {
            return selectedScenario;
        }
        return String.join(",", sanitized);
    }

    private String sanitizeDatasetId(String datasetId) {
        if (datasetId == null || datasetId.isBlank()) {
            return demoDatasetCatalog.selectedDatasetId();
        }
        String normalized = datasetId.trim().toLowerCase();
        if (demoDatasetCatalog.availableDatasetIds().contains(normalized)) {
            return normalized;
        }
        return demoDatasetCatalog.selectedDatasetId();
    }

    private String sanitizeCompareDatasets(String compareDatasets, String selectedDatasetId) {
        if (compareDatasets == null || compareDatasets.isBlank()) {
            return selectedDatasetId;
        }
        List<String> supportedDatasetIds = demoDatasetCatalog.availableDatasetIds();
        List<String> sanitized = Arrays.stream(compareDatasets.split(","))
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(value -> !value.isBlank())
            .filter(supportedDatasetIds::contains)
            .distinct()
            .limit(2)
            .collect(Collectors.toCollection(java.util.ArrayList::new));
        if (!sanitized.contains(selectedDatasetId) && sanitized.size() < 2) {
            sanitized.add(selectedDatasetId);
        }
        if (sanitized.isEmpty()) {
            sanitized.add(selectedDatasetId);
        }
        return String.join(",", sanitized);
    }
}
