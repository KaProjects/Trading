package org.kaleta.firebase;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.internal.NonNull;
import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.logging.Log;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.kaleta.model.FirebaseAsset;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.FirebaseCompanyDep;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@IfBuildProperty(name = "firebase.mode", stringValue = "real", enableIfMissing = true)
public class RealtimeFirebaseStore implements FirebaseStore
{
    private static final class FirebasePath
    {
        private static final String COMPANY_DEP = "company-dep";
        private static final String COMPANY = "company";
        private static final String ASSET = "asset";
    }

    private final FirebaseDatabase database;
    private final Map<String, FirebaseCompanyDep> companiesDep = new ConcurrentHashMap<>();
    private final Map<String, FirebaseCompany> companies = new ConcurrentHashMap<>();

    public RealtimeFirebaseStore(
            FirebaseApp app,
            @ConfigProperty(name = "firebase.db.url") String databaseUrl)
    {
        database = FirebaseDatabase.getInstance(app, databaseUrl);
        database.getReference(FirebasePath.COMPANY_DEP)
                .addValueEventListener(createListener(companiesDep, FirebaseCompanyDep.class));
        database.getReference(FirebasePath.COMPANY)
                .addValueEventListener(createListener(companies, FirebaseCompany.class));
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
    public void replaceAssets(List<FirebaseAsset> assets)
    {
        DatabaseReference reference = database.getReference(FirebasePath.ASSET);
        reference.removeValueAsync();
        for (FirebaseAsset asset : assets) {
            reference.push().setValue(asset, (databaseError, databaseReference) -> {});
        }
    }

    @Override
    public void saveQuarter(String ticker, String quarterId, FirebaseCompany.Gemini.Quarter quarter)
    {
        database.getReference(FirebasePath.COMPANY)
                .child(ticker + "/gemini/quarters/" + quarterId)
                .setValue(quarter, (databaseError, databaseReference) -> {});
    }

    private static <T> ValueEventListener createListener(Map<String, T> map, Class<T> clazz)
    {
        return new ValueEventListener()
        {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot)
            {
                map.clear();
                for (DataSnapshot data : dataSnapshot.getChildren()) {
                    try {
                        map.put(data.getKey(), data.getValue(clazz));
                    } catch (Exception exception) {
                        Log.error(exception.getMessage(), exception);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError)
            {
                Log.error("Firebase listener was cancelled: " + databaseError.getMessage());
            }
        };
    }
}
