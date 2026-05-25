package com.budgetflow.demo.fintech.dashboard;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
public class DemoDatasetCatalog {
    private static final Pattern DATASET_PATTERN = Pattern.compile("[a-z0-9-]+(?:/[a-z0-9-]+)+");
    private static final String DEFAULT_DATASET = "seed/default";
    private static final List<String> AVAILABLE_DATASET_IDS = List.of(
        "seed/default",
        "scenarios/stable-monthly-income",
        "scenarios/irregular-gig-income",
        "scenarios/high-subscription-load",
        "scenarios/overspending-user",
        "scenarios/low-cash-buffer",
        "scenarios/many-small-card-transactions",
        "scenarios/paycheck-to-paycheck-user"
    );

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final String selectedDatasetId;
    private final Map<String, DatasetPack> cache = new ConcurrentHashMap<>();

    public static DemoDatasetCatalog seedDefaultCatalog() {
        return new DemoDatasetCatalog(
            new DefaultResourceLoader(),
            JsonMapper.builder().findAndAddModules().build(),
            DEFAULT_DATASET
        );
    }

    public DemoDatasetCatalog(
        ResourceLoader resourceLoader,
        ObjectMapper objectMapper,
        @Value("${budgetflow.demo.dataset:" + DEFAULT_DATASET + "}") String selectedDatasetId
    ) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.selectedDatasetId = normalizeDatasetId(selectedDatasetId);
        loadDataset(this.selectedDatasetId);
    }

    public String selectedDatasetId() {
        return selectedDatasetId;
    }

    public List<String> availableDatasetIds() {
        return AVAILABLE_DATASET_IDS;
    }

    public Balance resolveBalance(String accountId) {
        DatasetPack datasetPack = loadDataset(selectedDatasetId);
        return resolveBalance(datasetPack, accountId);
    }

    public Balance resolveBalance(String accountId, String datasetId) {
        DatasetPack datasetPack = loadDataset(datasetId);
        return resolveBalance(datasetPack, accountId);
    }

    private Balance resolveBalance(DatasetPack datasetPack, String accountId) {
        DemoAccount account = datasetPack.accountById().get(accountId);
        if (account != null) {
            return new Balance(account.accountId(), account.availableBalance());
        }
        return new Balance(accountId, BigDecimal.ZERO);
    }

    public List<Transaction> resolveTransactions(String accountId) {
        DatasetPack datasetPack = loadDataset(selectedDatasetId);
        return resolveTransactions(datasetPack, accountId);
    }

    public List<Transaction> resolveTransactions(String accountId, String datasetId) {
        DatasetPack datasetPack = loadDataset(datasetId);
        return resolveTransactions(datasetPack, accountId);
    }

    private List<Transaction> resolveTransactions(DatasetPack datasetPack, String accountId) {
        return datasetPack.transactionsByAccountId()
            .getOrDefault(accountId, List.of())
            .stream()
            .sorted(Comparator.comparing(DemoTransaction::postedAt).reversed())
            .map(entry -> new Transaction(entry.id(), entry.merchant(), entry.amount()))
            .toList();
    }

    public DatasetPack loadDataset(String datasetId) {
        String safeDatasetId = normalizeDatasetId(datasetId);
        return cache.computeIfAbsent(safeDatasetId, this::readDatasetPack);
    }

    private DatasetPack readDatasetPack(String datasetId) {
        List<DemoCustomer> customers = readList(datasetId, "customers.json", DemoCustomer.class);
        List<DemoAccount> accounts = readList(datasetId, "accounts.json", DemoAccount.class);
        List<DemoTransaction> transactions = readList(datasetId, "transactions.json", DemoTransaction.class);
        List<DemoBudget> budgets = readList(datasetId, "budgets.json", DemoBudget.class);
        DemoScenarioMetadata metadata = readMetadata(datasetId);

        Map<String, DemoAccount> accountsById = accounts.stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(DemoAccount::accountId, account -> account));
        Map<String, List<DemoTransaction>> transactionsByAccountId = transactions.stream()
            .collect(java.util.stream.Collectors.groupingBy(DemoTransaction::accountId));
        return new DatasetPack(datasetId, customers, accounts, transactions, budgets, metadata, accountsById, transactionsByAccountId);
    }

    private DemoScenarioMetadata readMetadata(String datasetId) {
        Resource metadata = resourceLoader.getResource("classpath:demo-data/" + datasetId + "/scenario-metadata.json");
        if (!metadata.exists()) {
            return new DemoScenarioMetadata(
                datasetId,
                "Seed / baseline demo dataset",
                "Baseline fintech demo data used for default walkthroughs and smoke checks.",
                "Expect stable evaluator behavior and deterministic sample responses.",
                "Synthetic baseline consumer profiles with predictable balances and recurring spend.",
                "Verify baseline budget fit, low degradation pressure, and stable planner decisions.",
                "Representative synthetic baseline traffic.",
                "All records are synthetic and sanitized. No production or personal data."
            );
        }
        return readValue(metadata, DemoScenarioMetadata.class);
    }

    private <T> List<T> readList(String datasetId, String fileName, Class<T> elementType) {
        Resource resource = resource("classpath:demo-data/" + datasetId + "/" + fileName);
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, listType);
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Failed to read demo dataset file '" + fileName + "' for dataset '" + datasetId + "'",
                exception
            );
        }
    }

    private <T> T readValue(Resource resource, Class<T> valueType) {
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, valueType);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read demo dataset metadata from " + resource, exception);
        }
    }

    private Resource resource(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Missing demo dataset resource: " + location);
        }
        return resource;
    }

    private String normalizeDatasetId(String datasetId) {
        String candidate = datasetId == null ? DEFAULT_DATASET : datasetId.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (candidate.startsWith("/")) {
            candidate = candidate.substring(1);
        }
        if (candidate.endsWith("/")) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        if (candidate.isBlank()) {
            candidate = DEFAULT_DATASET;
        }
        if (!DATASET_PATTERN.matcher(candidate).matches() || candidate.contains("..")) {
            throw new IllegalArgumentException("Unsupported demo dataset id: " + datasetId);
        }
        return candidate;
    }

    public record DatasetPack(
        String datasetId,
        List<DemoCustomer> customers,
        List<DemoAccount> accounts,
        List<DemoTransaction> transactions,
        List<DemoBudget> budgets,
        DemoScenarioMetadata scenarioMetadata,
        Map<String, DemoAccount> accountById,
        Map<String, List<DemoTransaction>> transactionsByAccountId
    ) {
    }

    public record DemoCustomer(
        String customerId,
        String firstName,
        String lastName,
        String email,
        String segment
    ) {
    }

    public record DemoAccount(
        String accountId,
        String customerId,
        String accountType,
        String nickname,
        String currency,
        BigDecimal availableBalance,
        BigDecimal currentBalance
    ) {
    }

    public record DemoTransaction(
        String id,
        String accountId,
        OffsetDateTime postedAt,
        String merchant,
        String category,
        BigDecimal amount,
        String direction,
        String channel,
        String description
    ) {
    }

    public record DemoBudget(
        String budgetId,
        String customerId,
        String category,
        BigDecimal monthlyLimit,
        BigDecimal spentToDate,
        String period
    ) {
    }

    public record DemoScenarioMetadata(
        String scenarioId,
        String displayName,
        String intent,
        String expectedEvaluatorBehavior,
        String customerProfileSummary,
        String whatToLookFor,
        String realWorldPattern,
        String sanitizedNotice
    ) {
    }
}
