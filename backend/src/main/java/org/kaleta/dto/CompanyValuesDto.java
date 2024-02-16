package org.kaleta.dto;

import lombok.Data;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Sector;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompanyValuesDto
{
    private List<Currency> currencies = new ArrayList<>();
    private List<SectorDto> sectors = new ArrayList<>();

    public CompanyValuesDto()
    {
        currencies.addAll(List.of(Currency.values()));
        List.of(Sector.values()).forEach(sector -> sectors.add(SectorDto.from(sector)));
        sectors.sort(SectorDto::compare);
    }
}
