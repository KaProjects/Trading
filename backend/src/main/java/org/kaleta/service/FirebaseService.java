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
import io.quarkus.logging.Log;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.ConfigProvider;
import org.kaleta.model.FirebaseAsset;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.FirebaseCompanyDep;
import org.kaleta.model.Trades;
import org.kaleta.persistence.entity.Period;
import org.kaleta.rest.dto.PeriodImportDto;
import org.kaleta.rest.error.InvalidInputException;
import org.kaleta.rest.error.ServiceFailureException;

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
    private static final class Path {
        private static final String COMPANY_DEP = "company-dep";
        private static final String COMPANY = "company";
        private static final String ASSET = "asset";
    }
    private static final Map<String, FirebaseCompanyDep> companiesDep = new HashMap<>();
    private static final Map<String, FirebaseCompany> companies = new HashMap<>();

    static {
        if (checkAccess())
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
            database.getReference(Path.COMPANY_DEP).addValueEventListener(createListener(companiesDep, FirebaseCompanyDep.class));
            database.getReference(Path.COMPANY).addValueEventListener(createListener(companies, FirebaseCompany.class));
        }
    }

    public boolean hasCompany(String ticker)
    {
        return companiesDep.get(ticker) != null;
    }

    public FirebaseCompanyDep getCompanyDep(String ticker)
    {
        FirebaseCompanyDep  company = companiesDep.get(ticker);
        if  (company == null) throw new InvalidInputException("company with ticker '" + ticker + "' not found");
        return company;
    }

    public void pushAssets(Trades activeTrades)
    {
        if (checkAccess())
        {
            DatabaseReference db = database.getReference(Path.ASSET);
            db.removeValueAsync();
            for (Trades.Trade trade : activeTrades.getTrades())
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

    public PeriodImportDto getPeriod(String ticker, String quarterId)
    {
        FirebaseCompany company = companies.get(ticker);
        if (company == null || company.getGemini() == null) return null;
        FirebaseCompany.Gemini.Quarter quarter = company.getGemini().getQuarters().get(quarterId);
        if (quarter == null) return null;
        return quarter.toImportDto();
    }

    public void updatePeriod(Period period)
    {
        if (checkAccess())
        {
            String ticker = period.getCompany().getTicker();
            FirebaseCompany company = companies.get(ticker);
            if (company == null || company.getGemini() == null) return;
            String quarterId = period.getName().toString();
            FirebaseCompany.Gemini.Quarter quarter = company.getGemini().getQuarters().get(quarterId);
            if (quarter == null) return;

            quarter.setReport_date_this_quarter(String.valueOf(period.getReportDate()));
            quarter.setReported_shares(String.valueOf(period.getShares()));
            quarter.setPrice_min(String.valueOf(period.getPriceLow()));
            quarter.setPrice_max(String.valueOf(period.getPriceHigh()));
            quarter.setReported_revenues(String.valueOf(period.getRevenue()));
            quarter.setReported_gross_profit(String.valueOf(period.getGrossProfit()));
            quarter.setReported_operating_income(String.valueOf(period.getOperatingIncome()));
            quarter.setReported_net_income(String.valueOf(period.getNetIncome()));
            quarter.setReported_div(String.valueOf(period.getDividend()));

            database.getReference(Path.COMPANY)
                    .child( ticker + "/gemini/quarters/" + quarterId)
                    .setValue(quarter, (databaseError, databaseReference) -> {});
        }
    }

    private static boolean checkAccess() {
        return ConfigProvider.getConfig().getValue("environment", String.class).equals("PRODUCTION");
    }

    private static <T> ValueEventListener createListener(Map<String, T> map, Class<T> clazz) {
        return new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
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
            public void onCancelled(@NonNull DatabaseError databaseError) {}
        };
    }
}
