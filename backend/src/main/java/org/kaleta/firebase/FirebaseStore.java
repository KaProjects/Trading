package org.kaleta.firebase;

import org.kaleta.model.FirebaseAsset;
import org.kaleta.model.FirebaseCompany;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FirebaseStore
{
    record QuarterMetadata(String endingMonth, boolean reported) {}

    Map<String, QuarterMetadata> findQuartersMetadata(String ticker);

    Optional<FirebaseCompany.Gemini.Info> findGeminiInfo(String ticker);

    Optional<FirebaseCompany.Gemini.Quarter> findQuarter(String ticker, String quarterId);

    Map<String, FirebaseCompany.FinnhubEarnings> findEarnings(String ticker, String quarterId);

    Map<String, FirebaseCompany.Gemini.Target> findTargets(String ticker);

    Map<String, FirebaseCompany.NewsSentiment> findNewsSentiments(
            String ticker,
            LocalDate startInclusive,
            LocalDate endExclusive);

    Map<String, FirebaseCompany.NewsSentiment> findLatestNewsSentiments(String ticker);

    void replaceAssets(List<FirebaseAsset> assets);

    void updateQuarter(String ticker, String quarterId, FirebaseCompany.Gemini.Quarter quarter);
}
