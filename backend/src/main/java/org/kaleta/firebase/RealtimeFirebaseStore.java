package org.kaleta.firebase;

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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        private static final String TARGETS = "targets";
        private static final String FINNHUB_EARNINGS = "fhe";
    }

    private final FirebaseDatabase database;

    public RealtimeFirebaseStore(
            FirebaseApp app,
            @ConfigProperty(name = "firebase.db.url") String databaseUrl)
    {
        database = FirebaseDatabase.getInstance(app, databaseUrl);
    }

    @Override
    public Map<String, QuarterMetadata> findQuartersMetadata(String ticker)
    {
        DataSnapshot quarters = read(company(ticker)
                .child(FirebasePath.GEMINI)
                .child(FirebasePath.QUARTERS));
        Map<String, QuarterMetadata> result = new LinkedHashMap<>();
        for (DataSnapshot quarter : quarters.getChildren()) {
            String revenues = quarter.child("reported_revenues").getValue(String.class);
            result.put(quarter.getKey(), new QuarterMetadata(
                    quarter.child("ending_month").getValue(String.class),
                    revenues != null && !revenues.isBlank()));
        }
        return Map.copyOf(result);
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
    public Map<String, FirebaseCompany.Gemini.Target> findTargets(String ticker)
    {
        return readChildren(company(ticker)
                .child(FirebasePath.GEMINI)
                .child(FirebasePath.TARGETS), FirebaseCompany.Gemini.Target.class);
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
