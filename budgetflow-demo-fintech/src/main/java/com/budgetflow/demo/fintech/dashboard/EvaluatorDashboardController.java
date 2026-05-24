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

    public EvaluatorDashboardController(EvaluatorDashboardService evaluatorDashboardService) {
        this.evaluatorDashboardService = evaluatorDashboardService;
    }

    @GetMapping(value = "/dashboard/evaluator", produces = MediaType.TEXT_HTML_VALUE)
    public String evaluatorDashboard(
        @RequestParam(name = "pack", defaultValue = "default") String packName,
        @RequestParam(name = "scenario", required = false) String scenarioName,
        @RequestParam(name = "profile", defaultValue = "balanced") String profileName,
        @RequestParam(name = "compareProfiles", defaultValue = "balanced,continuity,efficiency") String compareProfiles
    ) {
        String safePack = sanitizePack(packName);
        DashboardScenarioPack resolvedPack = PressureScenarios.packNamed(safePack);
        PlannerPolicyProfile safeProfile = sanitizeProfile(profileName);
        String safeScenario = sanitizeScenarioName(scenarioName, resolvedPack);
        String safeCompareProfiles = sanitizeCompareProfiles(compareProfiles, safeProfile);
        return evaluatorDashboardService.render(
            safePack,
            safeScenario,
            safeProfile.configName(),
            safeCompareProfiles
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
}
