package org.kaleta.firebase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.model.FirebaseAsset;
import org.kaleta.model.FirebaseCompany;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Singleton
@Startup
@IfBuildProperty(name = "firebase.mode", stringValue = "fake")
public class InMemoryFirebaseStore implements FirebaseStore
{
    private final Map<String, FirebaseCompany> companies = new ConcurrentHashMap<>();
    private final List<FirebaseAsset> assets = new CopyOnWriteArrayList<>();

    @Inject
    public InMemoryFirebaseStore(
            ObjectMapper objectMapper,
            FirebaseSnapshotDownloader snapshotDownloader,
            @ConfigProperty(name = "firebase.data.file") String dataFile)
    {
        Path snapshotFile = Path.of(dataFile).toAbsolutePath().normalize();
        createSnapshotIfMissing(objectMapper, snapshotDownloader, snapshotFile);
        load(objectMapper, snapshotFile);
    }

    @Override
    public Map<String, QuarterMetadata> findQuartersMetadata(String ticker)
    {
        FirebaseCompany company = companies.get(ticker);
        if (company == null || company.getGemini() == null || company.getGemini().getQuarters() == null) {
            return Map.of();
        }
        return company.getGemini().getQuarters().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> {
                            FirebaseCompany.Gemini.Quarter quarter = entry.getValue();
                            String revenues = quarter.getReported_revenues();
                            return new QuarterMetadata(
                                    quarter.getEnding_month(),
                                    revenues != null && !revenues.isBlank());
                        }));
    }

    @Override
    public Optional<FirebaseCompany.Gemini.Quarter> findQuarter(String ticker, String quarterId)
    {
        FirebaseCompany company = companies.get(ticker);
        if (company == null || company.getGemini() == null || company.getGemini().getQuarters() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(company.getGemini().getQuarters().get(quarterId));
    }

    @Override
    public Map<String, FirebaseCompany.FinnhubEarnings> findEarnings(String ticker, String quarterId)
    {
        FirebaseCompany company = companies.get(ticker);
        if (company == null || company.getFhe() == null || company.getFhe().get(quarterId) == null) {
            return Map.of();
        }
        return Map.copyOf(company.getFhe().get(quarterId));
    }

    @Override
    public Map<String, FirebaseCompany.Gemini.Target> findTargets(String ticker)
    {
        FirebaseCompany company = companies.get(ticker);
        if (company == null || company.getGemini() == null || company.getGemini().getTargets() == null) {
            return Map.of();
        }
        return Map.copyOf(company.getGemini().getTargets());
    }

    @Override
    public void replaceAssets(List<FirebaseAsset> newAssets)
    {
        assets.clear();
        assets.addAll(newAssets);
    }

    @Override
    public void updateQuarter(String ticker, String quarterId, FirebaseCompany.Gemini.Quarter update)
    {
        FirebaseCompany.Gemini.Quarter quarter = findQuarter(ticker, quarterId).orElse(null);
        if (quarter == null) return;

        quarter.setReport_date_this_quarter(update.getReport_date_this_quarter());
        quarter.setReported_shares(update.getReported_shares());
        quarter.setPrice_min(update.getPrice_min());
        quarter.setPrice_max(update.getPrice_max());
        quarter.setReported_revenues(update.getReported_revenues());
        quarter.setReported_gross_profit(update.getReported_gross_profit());
        quarter.setReported_operating_income(update.getReported_operating_income());
        quarter.setReported_net_income(update.getReported_net_income());
        quarter.setReported_div(update.getReported_div());
        quarter.setReported_eps(update.getReported_eps());
    }

    List<FirebaseAsset> getAssets()
    {
        return List.copyOf(assets);
    }

    private void createSnapshotIfMissing(
            ObjectMapper objectMapper,
            FirebaseSnapshotDownloader snapshotDownloader,
            Path snapshotFile)
    {
        if (Files.exists(snapshotFile)) {
            return;
        }

        Log.infof("Local Firebase snapshot is missing; downloading company data to %s", snapshotFile);
        try {
            Path parent = snapshotFile.getParent();
            Files.createDirectories(parent);

            Path temporaryFile = Files.createTempFile(parent, "firebase-", ".json.tmp");
            try {
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(temporaryFile.toFile(), snapshotDownloader.download());
                moveSnapshot(temporaryFile, snapshotFile);
            } finally {
                Files.deleteIfExists(temporaryFile);
            }
            Log.infof("Local Firebase snapshot created at %s", snapshotFile);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to create local Firebase snapshot at '" + snapshotFile + "'",
                    exception);
        }
    }

    private void moveSnapshot(Path temporaryFile, Path snapshotFile) throws IOException
    {
        try {
            Files.move(temporaryFile, snapshotFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, snapshotFile);
        }
    }

    private void load(ObjectMapper objectMapper, Path snapshotFile)
    {
        try (InputStream input = Files.newInputStream(snapshotFile)) {
            FirebaseData data = objectMapper.readValue(input, FirebaseData.class);
            if (data.companies != null) {
                companies.putAll(data.companies);
            }
            if (data.assets != null) {
                assets.addAll(data.assets);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to initialize fake Firebase from '" + snapshotFile + "'",
                    exception);
        }
    }

    private static class FirebaseData
    {
        @JsonProperty("company")
        private Map<String, FirebaseCompany> companies;
        @JsonProperty("asset")
        private List<FirebaseAsset> assets = new ArrayList<>();
    }
}
