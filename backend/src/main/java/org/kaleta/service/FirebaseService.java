package org.kaleta.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.internal.NonNull;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.ConfigProvider;
import org.kaleta.model.FirebaseAsset;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.FirebaseCompanyDep;
import org.kaleta.persistence.entity.Trade;
import org.kaleta.rest.dto.PeriodImportDto;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class FirebaseService
{
    private static FirebaseDatabase database;
    private static final Map<String, FirebaseCompanyDep> companiesDep = new HashMap<>();
    private static final Map<String, FirebaseCompany> companies = new HashMap<>();

    static {
        if (ConfigProvider.getConfig().getValue("environment", String.class).equals("PRODUCTION"))
        {
            FirebaseOptions options;
            try {
                InputStream serviceKeyStream = FirebaseService.class.getResourceAsStream("/cert.json");

                options = new FirebaseOptions.Builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceKeyStream))
                        .setDatabaseUrl(ConfigProvider.getConfig().getValue("firebase.db.url", String.class))
                        .build();
            } catch (IOException e) {
                throw new ServiceFailureException(e);
            }

            FirebaseApp app;
            if (FirebaseApp.getApps().isEmpty()){
                app = FirebaseApp.initializeApp(options);
            } else {
                app = FirebaseApp.getInstance();
            }
            database = FirebaseDatabase.getInstance(app);
            database.getReference("company-dep").addValueEventListener(createListener(companiesDep, FirebaseCompanyDep.class));
            database.getReference("company").addValueEventListener(createListener(companies, FirebaseCompany.class));
        }
    }

    public boolean hasCompany(String ticker)
    {
        return companiesDep.get(ticker) != null;
    }

    public FirebaseCompanyDep getCompanyDep(String ticker)
    {
        FirebaseCompanyDep  company = companiesDep.get(ticker);
        if  (company == null) throw new ServiceFailureException("company with ticker '" + ticker + "' not found");
        return company;
    }

    public void pushAssets(List<Trade> activeTrades)
    {
        if (ConfigProvider.getConfig().getValue("environment", String.class).equals("PRODUCTION"))
        {
            DatabaseReference db = database.getReference("asset");
            db.removeValueAsync();
            for (Trade trade : activeTrades)
            {
                db.push().setValue(FirebaseAsset.from(trade), (databaseError, databaseReference) -> {});
            }
        }
    }

    public List<PeriodImportDto> getNewerPeriods(String ticker, String quarterId)
    {
        FirebaseCompany company = companies.get(ticker);
        if (company == null || company.getGemini() == null) return new ArrayList<>();
        return company.getGemini().getQuarters().values().stream()
                .filter(quarter -> quarter.isInFutureOf(quarterId))
                .sorted(Comparator.comparing(FirebaseCompany.Gemini.Quarter::getId).reversed())
                .map(FirebaseCompany.Gemini.Quarter::toImportDto)
                .collect(Collectors.toList());
    }

    private static <T> ValueEventListener createListener(Map<String, T> map, Class<T> clazz) {
        return new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                map.clear();
                for (DataSnapshot data : dataSnapshot.getChildren()) {
                    try {
                        map.put(data.getKey(), data.getValue(clazz));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
        };
    }
}
