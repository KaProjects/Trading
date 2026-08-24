package org.kaleta.firebase;

import org.kaleta.model.FirebaseAsset;
import org.kaleta.model.FirebaseCompany;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FirebaseStore
{
    record QuarterMetadata(String endingMonth, boolean reported) {}

    Map<String, QuarterMetadata> findQuartersMetadata(String ticker);

    Optional<FirebaseCompany.Gemini.Quarter> findQuarter(String ticker, String quarterId);

    Map<String, FirebaseCompany.FinnhubEarnings> findEarnings(String ticker, String quarterId);

    Map<String, FirebaseCompany.Gemini.Target> findTargets(String ticker);

    void replaceAssets(List<FirebaseAsset> assets);

    void updateQuarter(String ticker, String quarterId, FirebaseCompany.Gemini.Quarter quarter);
}
