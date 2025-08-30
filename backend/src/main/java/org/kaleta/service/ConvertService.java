package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.Utils;
import org.kaleta.dto.SectorDto;
import org.kaleta.entity.Sector;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;

@ApplicationScoped
public class ConvertService
{
    public Date parse(String date)
    {
        if (Utils.isValidDbDate(date)){
            return Date.valueOf(date);
        } else {
            throw new ServiceFailureException("invalid date format '" + date + "' not YYYY-MM-DD");
        }
    }

    public String format(Date date)
    {
        if (date == null) return null;
        String[] split = date.toString().split("-");
        return split[2] + "." + split[1] + "." + split[0];
    }

    public SectorDto from(Sector sector)
    {
        if (sector == null) return null;
        SectorDto dto = new SectorDto();
        dto.setKey(sector.toString());
        dto.setName(sector.getName());
        return dto;
    }

    public String format(BigDecimal value)
    {
        if (value == null) return "";
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * Examples:
     * 100 -> 100M
     * 1100 -> 1.10B
     *
     * @return formatted value of millions
     */
    public String formatMillions(BigDecimal value)
    {
        if (value == null) return "";
        if (value.compareTo(new BigDecimal(1000)) > 0) {
            return format(value.divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP)) + "B";
        } else {
            return format(value) + "M";
        }
    }
}
