package org.kaleta.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
public class DividendsUiDto
{
    private List<String> columns = new ArrayList<>();
    private List<DividendDto> dividends = new ArrayList<>();
    private String[] sums = new String[14];

    public DividendsUiDto()
    {
        columns.add("Ticker");
        columns.add("#");
        columns.add("Date");
        columns.add("Dividend");
        columns.add("Tax");
        columns.add("Total");
        Arrays.fill(sums, "");
    }

    public static DividendsUiDto from(List<org.kaleta.entity.Dividend> dividends)
    {
        DividendsUiDto dividendsUiDto = new DividendsUiDto();

        for (org.kaleta.entity.Dividend dividend : dividends)
        {
            dividendsUiDto.getDividends().add(DividendDto.from(dividend));
        }
        dividendsUiDto.getDividends().sort(DividendDto::compareTo);
        return dividendsUiDto;
    }
}
