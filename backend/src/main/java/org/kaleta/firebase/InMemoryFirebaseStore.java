package org.kaleta.firebase;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.model.FirebaseAsset;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.FirebaseCompanyDep;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
@IfBuildProperty(name = "firebase.mode", stringValue = "fake")
public class InMemoryFirebaseStore implements FirebaseStore
{
    private final Map<String, FirebaseCompanyDep> companiesDep = new ConcurrentHashMap<>();
    private final Map<String, FirebaseCompany> companies = new ConcurrentHashMap<>();
    private final List<FirebaseAsset> assets = new CopyOnWriteArrayList<>();

    @Inject
    public InMemoryFirebaseStore(
            ObjectMapper objectMapper,
            @ConfigProperty(name = "firebase.data.file") Optional<String> dataFile)
    {
        dataFile.filter(path -> !path.isBlank())
                .ifPresent(path -> load(objectMapper, Path.of(path)));
    }

    @Override
    public Optional<FirebaseCompanyDep> findCompanyDep(String ticker)
    {
        return Optional.ofNullable(companiesDep.get(ticker));
    }

    @Override
    public Optional<FirebaseCompany> findCompany(String ticker)
    {
        return Optional.ofNullable(companies.get(ticker));
    }

    @Override
    public void replaceAssets(List<FirebaseAsset> newAssets)
    {
        assets.clear();
        assets.addAll(newAssets);
    }

    @Override
    public void saveQuarter(String ticker, String quarterId, FirebaseCompany.Gemini.Quarter quarter)
    {
        FirebaseCompany company = companies.get(ticker);
        if (company == null || company.getGemini() == null || company.getGemini().getQuarters() == null) {
            return;
        }
        company.getGemini().getQuarters().put(quarterId, quarter);
    }

    List<FirebaseAsset> getAssets()
    {
        return List.copyOf(assets);
    }

    private void load(ObjectMapper objectMapper, Path dataFile)
    {
        try (InputStream input = Files.newInputStream(dataFile)) {
            FirebaseData data = objectMapper.readValue(input, FirebaseData.class);
            if (data.companiesDep != null) {
                companiesDep.putAll(data.companiesDep);
            }
            if (data.companies != null) {
                companies.putAll(data.companies);
            }
            if (data.assets != null) {
                assets.addAll(data.assets);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to initialize fake Firebase from '" + dataFile + "'", exception);
        }
    }

    private static class FirebaseData
    {
        @JsonProperty("company-dep")
        private Map<String, FirebaseCompanyDep> companiesDep;
        @JsonProperty("company")
        private Map<String, FirebaseCompany> companies;
        @JsonProperty("asset")
        private List<FirebaseAsset> assets = new ArrayList<>();
    }
}
