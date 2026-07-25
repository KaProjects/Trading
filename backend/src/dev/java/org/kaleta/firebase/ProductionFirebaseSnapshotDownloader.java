package org.kaleta.firebase;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.internal.NonNull;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
public class ProductionFirebaseSnapshotDownloader implements FirebaseSnapshotDownloader
{
    private static final long DOWNLOAD_TIMEOUT_SECONDS = 30;
    private static final String COMPANY_DEP = "company-dep";
    private static final String COMPANY = "company";
    private static final String ASSET = "asset";

    private final Instance<FirebaseApp> firebaseApp;
    private final String databaseUrl;

    @Inject
    public ProductionFirebaseSnapshotDownloader(
            Instance<FirebaseApp> firebaseApp,
            @ConfigProperty(name = "firebase.db.url") String databaseUrl)
    {
        this.firebaseApp = firebaseApp;
        this.databaseUrl = databaseUrl;
    }

    @Override
    public Map<String, Object> download()
    {
        FirebaseDatabase database = FirebaseDatabase.getInstance(firebaseApp.get(), databaseUrl);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put(COMPANY_DEP, download(database.getReference(COMPANY_DEP)));
        snapshot.put(COMPANY, download(database.getReference(COMPANY)));
        snapshot.put(ASSET, List.of());
        return snapshot;
    }

    private Object download(DatabaseReference reference)
    {
        CompletableFuture<Object> result = new CompletableFuture<>();
        ValueEventListener listener = new ValueEventListener()
        {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot)
            {
                Object value = snapshot.getValue();
                result.complete(value == null ? Map.of() : value);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error)
            {
                result.completeExceptionally(error.toException());
            }
        };
        reference.addListenerForSingleValueEvent(listener);

        try {
            return result.get(DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Firebase snapshot download was interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(
                    "Failed to download Firebase path '" + reference.getKey() + "'",
                    exception.getCause());
        } catch (TimeoutException exception) {
            reference.removeEventListener(listener);
            throw new IllegalStateException(
                    "Timed out downloading Firebase path '" + reference.getKey() + "'",
                    exception);
        }
    }
}
