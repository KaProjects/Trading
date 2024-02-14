package org.kaleta.dto;

import lombok.Data;
import org.kaleta.Utils;
import org.kaleta.entity.Currency;
import org.kaleta.entity.Dividend;

import static org.kaleta.Utils.format;

@Data
public class DividendDto implements Comparable<DividendDto>
{
    private String id;
    private String ticker;
    private Currency currency;
    private String sector;

    private String date;
    private String dividend;
    private String tax;

    private String total;

    @Override
    public int compareTo(DividendDto other)
    {
        return -Utils.compareDtoDates(this.getDate(), other.getDate());
    }

    public static DividendDto from(Dividend dividend)
    {
        DividendDto dto = new DividendDto();
        dto.setId(dividend.getId());
        dto.setTicker(dividend.getTicker());
        dto.setCurrency(dividend.getCurrency());
        dto.setSector(dividend.getCompany().getSector());
        dto.setDate(Utils.format(dividend.getDate()));
        dto.setDividend(format(dividend.getDividend()));
        dto.setTax(format(dividend.getTax()));
        dto.setTotal(format(dividend.getTotal()));
        return dto;
    }
}
