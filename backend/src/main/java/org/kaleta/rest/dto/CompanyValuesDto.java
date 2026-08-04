package org.kaleta.rest.dto;

import lombok.Data;
import org.kaleta.model.Company;
import org.kaleta.model.Trades;
import org.kaleta.persistence.entity.Currency;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompanyValuesDto
{
    private List<Currency> currencies = new ArrayList<>();
    private List<Company.Sector> sectors = new ArrayList<>();
    private List<Trades.Portfolio> portfolios = new ArrayList<>();
    private List<Company> companies = new ArrayList<>();
    private List<Company> recentCompanies = new ArrayList<>();
    private List<String> years = new ArrayList<>();
}
