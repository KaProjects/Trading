package org.kaleta.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.internal.NonNull;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.ConfigProvider;
import org.kaleta.model.FirebaseCompany;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class FirebaseService
{
    static {
        String databaseUrl = ConfigProvider.getConfig().getValue("firebase.db.url", String.class);

        FirebaseOptions options;
        try {
            InputStream serviceKeyStream = FirebaseService.class.getResourceAsStream("/cert.json");

            options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceKeyStream))
                    .setDatabaseUrl(databaseUrl)
                    .build();
        } catch (IOException e) {
            throw new ServiceFailureException(e);
        }

        FirebaseApp app = FirebaseApp.initializeApp(options);
        FirebaseDatabase database = FirebaseDatabase.getInstance(app);

        database.getReference("company").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                companies.clear();
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    try {
                        FirebaseCompany company = postSnapshot.getValue(FirebaseCompany.class);
                        companies.add(company);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
        });
    }
    private static List<FirebaseCompany> companies = new ArrayList<>();

    public boolean hasCompany(String ticker)
    {
        return companies.stream().filter(company -> company.getTicker().equals(ticker)).collect(Collectors.toSet()).size() > 0;
    }

    public FirebaseCompany getCompany(String ticker)
    {
        return companies.stream()
                .filter(company -> company.getTicker().equals(ticker))
                .findFirst()
                .orElseThrow(() -> new ServiceFailureException("company with ticker '" + ticker + "' not found"));
    }
}
