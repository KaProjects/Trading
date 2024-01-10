package org.kaleta.dto;

import lombok.Data;
import org.kaleta.entity.Company;

import java.util.ArrayList;
import java.util.List;

@Data
public class CompanyDto implements Comparable<CompanyDto>
{
    private String id;
    private String ticker;
    private String currency;

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
            list.add(dto);
        }
        list.sort(CompanyDto::compareTo);
        return list;
    }
}
