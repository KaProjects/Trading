package org.kaleta.firebase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.Credentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.internal.NonNull;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.model.FirebaseAsset;
import org.kaleta.model.FirebaseCompany;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
@IfBuildProperty(name = "firebase.mode", stringValue = "real", enableIfMissing = true)
public class RealtimeFirebaseStore implements FirebaseStore
{
    private static final long READ_TIMEOUT_SECONDS = 10;

    private static final class FirebasePath
    {
        private static final String COMPANY = "company";
        private static final String ASSET = "asset";
        private static final String GEMINI = "gemini";
        private static final String QUARTERS = "quarters";
        private static final String FINNHUB_EARNINGS = "fhe";
    }

    private final FirebaseDatabase database;
    private final Credentials credentials;
    private final ObjectMapper objectMapper;
    private final String databaseUrl;

    public RealtimeFirebaseStore(
            FirebaseApp app,
            Credentials credentials,
            ObjectMapper objectMapper,
            @ConfigProperty(name = "firebase.db.url") String databaseUrl)
    {
        database = FirebaseDatabase.getInstance(app, databaseUrl);
        this.credentials = credentials;
        this.objectMapper = objectMapper;
        this.databaseUrl = databaseUrl.endsWith("/")
                ? databaseUrl.substring(0, databaseUrl.length() - 1)
                : databaseUrl;
    }

    @Override
    public Set<String> findQuarterIds(String ticker)
    {
        String encodedTicker = URLEncoder.encode(ticker, StandardCharsets.UTF_8);
        URI uri = URI.create(databaseUrl
                + "/company/" + encodedTicker
                + "/gemini/quarters.json?shallow=true");

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(READ_TIMEOUT_SECONDS));
            connection.setReadTimeout((int) TimeUnit.SECONDS.toMillis(READ_TIMEOUT_SECONDS));
            for (Map.Entry<String, List<String>> header : credentials.getRequestMetadata(uri).entrySet()) {
                for (String value : header.getValue()) {
                    connection.addRequestProperty(header.getKey(), value);
                }
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Firebase shallow read failed with HTTP status " + status);
            }

            try (InputStream response = connection.getInputStream()) {
                JsonNode keys = objectMapper.readTree(response);
                if (keys == null || !keys.isObject()) return Set.of();

                Set<String> result = new LinkedHashSet<>();
                keys.fieldNames().forEachRemaining(result::add);
                return Collections.unmodifiableSet(result);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read Firebase quarter keys", exception);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Override
    public QuarterMetadata findQuarterMetadata(String ticker, String quarterId)
    {
        DatabaseReference quarter = company(ticker)
                .child(FirebasePath.GEMINI)
                .child(FirebasePath.QUARTERS)
                .child(quarterId);
        String endingMonth = read(quarter.child("ending_month"), String.class).orElse(null);
        String revenues = read(quarter.child("reported_revenues"), String.class).orElse(null);
        return new QuarterMetadata(endingMonth, revenues != null && !revenues.isBlank());
    }

    @Override
    public Optional<FirebaseCompany.Gemini.Quarter> findQuarter(String ticker, String quarterId)
    {
        return read(company(ticker)
                .child(FirebasePath.GEMINI)
                .child(FirebasePath.QUARTERS)
                .child(quarterId), FirebaseCompany.Gemini.Quarter.class);
    }

    @Override
    public Map<String, FirebaseCompany.FinnhubEarnings> findEarnings(String ticker, String quarterId)
    {
        return readChildren(company(ticker)
                .child(FirebasePath.FINNHUB_EARNINGS)
                .child(quarterId), FirebaseCompany.FinnhubEarnings.class);
    }

    @Override
    public void replaceAssets(List<FirebaseAsset> assets)
    {
        DatabaseReference reference = database.getReference(FirebasePath.ASSET);
        reference.removeValueAsync();
        for (FirebaseAsset asset : assets) {
            reference.push().setValue(asset, (databaseError, databaseReference) -> {});
        }
    }

    @Override
    public void updateQuarter(String ticker, String quarterId, FirebaseCompany.Gemini.Quarter quarter)
    {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("report_date_this_quarter", quarter.getReport_date_this_quarter());
        values.put("reported_shares", quarter.getReported_shares());
        values.put("price_min", quarter.getPrice_min());
        values.put("price_max", quarter.getPrice_max());
        values.put("reported_revenues", quarter.getReported_revenues());
        values.put("reported_gross_profit", quarter.getReported_gross_profit());
        values.put("reported_operating_income", quarter.getReported_operating_income());
        values.put("reported_net_income", quarter.getReported_net_income());
        values.put("reported_div", quarter.getReported_div());
        values.put("reported_eps", quarter.getReported_eps());

        company(ticker)
                .child(FirebasePath.GEMINI)
                .child(FirebasePath.QUARTERS)
                .child(quarterId)
                .updateChildrenAsync(values);
    }

    private DatabaseReference company(String ticker)
    {
        return database.getReference(FirebasePath.COMPANY).child(ticker);
    }

    private <T> Optional<T> read(DatabaseReference reference, Class<T> clazz)
    {
        DataSnapshot snapshot = read(reference);
        return snapshot.exists() ? Optional.ofNullable(snapshot.getValue(clazz)) : Optional.empty();
    }

    private <T> Map<String, T> readChildren(DatabaseReference reference, Class<T> clazz)
    {
        DataSnapshot snapshot = read(reference);
        Map<String, T> result = new LinkedHashMap<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            result.put(child.getKey(), child.getValue(clazz));
        }
        return result;
    }

    private DataSnapshot read(DatabaseReference reference)
    {
        CompletableFuture<DataSnapshot> result = new CompletableFuture<>();
        ValueEventListener listener = new ValueEventListener()
        {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot)
            {
                result.complete(snapshot);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error)
            {
                result.completeExceptionally(error.toException());
            }
        };
        reference.addListenerForSingleValueEvent(listener);

        try {
            return result.get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firebase read was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(
                    "Failed to read Firebase path '" + reference.getKey() + "'",
                    exception.getCause());
        } catch (TimeoutException exception) {
            reference.removeEventListener(listener);
            throw new IllegalStateException(
                    "Timed out reading Firebase path '" + reference.getKey() + "'",
                    exception);
        }
    }
}
