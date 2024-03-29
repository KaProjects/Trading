package org.kaleta.dto;

import lombok.Data;
import org.kaleta.entity.Company;
import org.kaleta.entity.Currency;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompanyDto implements Comparable<CompanyDto>
{
    private String id;
    private String ticker;
    private Currency currency;
    private Boolean watching;
    private SectorDto sector;
    private String sharesFloat;

    @Override
    public int compareTo(CompanyDto other)

    {
        return this.getTicker().compareTo(other.getTicker());
    }

    public static List<CompanyDto> from(List<Company> companies)
    {
        List<CompanyDto> list = new ArrayList<>();
        for (Company company : companies)
        {
            CompanyDto dto = new CompanyDto();
            dto.setId(company.getId());
            dto.setTicker(company.getTicker());
            dto.setCurrency(company.getCurrency());
            dto.setWatching(company.isWatching());
            dto.setSector(SectorDto.from(company.getSector()));
            dto.setSharesFloat(company.getSharesFloat());
            list.add(dto);
        }
        list.sort(CompanyDto::compareTo);
        return list;
    }
}
