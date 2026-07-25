package org.kaleta.firebase;

import org.kaleta.model.FirebaseAsset;
import org.kaleta.model.FirebaseCompany;
import org.kaleta.model.FirebaseCompanyDep;

import java.util.List;
import java.util.Optional;

public interface FirebaseStore
{
    Optional<FirebaseCompanyDep> findCompanyDep(String ticker);

    Optional<FirebaseCompany> findCompany(String ticker);

    void replaceAssets(List<FirebaseAsset> assets);

    void saveQuarter(String ticker, String quarterId, FirebaseCompany.Gemini.Quarter quarter);
}
