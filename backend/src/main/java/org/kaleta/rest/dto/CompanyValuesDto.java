package org.kaleta.rest.dto;

import lombok.Data;
import org.kaleta.model.Company;
import org.kaleta.persistence.entity.Currency;
import org.kaleta.persistence.entity.Sector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Data
public class CompanyValuesDto
{
    private List<Currency> currencies = new ArrayList<>();
    private List<Company.Sector> sectors = new ArrayList<>();
    private List<Company> companies = new ArrayList<>();

    public CompanyValuesDto(){}
    public CompanyValuesDto(List<Company> companies)
    {
        currencies.addAll(List.of(Currency.values()));
        List.of(Sector.values()).forEach(sector -> sectors.add(new Company.Sector(sector)));
        sectors.sort(Company.Sector::compareTo);
        this.companies.addAll(companies);
        this.companies.sort(Comparator.comparing(Company::getTicker));
    }
}
