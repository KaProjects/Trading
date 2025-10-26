package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;

@ApplicationScoped
public class ConvertService
{
    @Deprecated
    public Date parseDate(String date)
    {
        if (date != null && Utils.isValidDbDate(date)){
            return Date.valueOf(date);
        } else {
            throw new ServiceFailureException("invalid date format '" + date + "' not YYYY-MM-DD");
        }
    }

    @Deprecated // TODO should be in UI
    public String format(Date date)
    {
        if (date == null) return "";
        String[] split = date.toString().split("-");
        return split[2] + "." + split[1] + "." + split[0];
    }

    @Deprecated // TODO should be in UI
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
    @Deprecated // TODO should be in UI
    public String formatMillions(BigDecimal value)
    {
        if (value == null) return "";
        if (value.abs().compareTo(new BigDecimal(1000)) >= 0) {
            return format(value.divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP)) + "B";
        } else {
            return format(value) + "M";
        }
    }

    /**
     * Examples:
     * 2502 -> 02/2025
     */
    @Deprecated // TODO should be in UI
    public String formatMonth(String value)
    {
        if (value == null) return "";
        if (!value.matches("\\d\\d\\d\\d"))
            throw new ServiceFailureException("Invalid Month: '" + value + "', expected YYMM or null");
        return value.substring(2) + "/20" + value.substring(0, 2);
    }

    /**
     * 10.52 -> 11
     * 10.12 -> 10
     */
    @Deprecated // TODO should be in UI
    public String formatNoDecimal(BigDecimal value)
    {
        if (value == null){
            return "";
        } else {
            return value.setScale(0, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
    }
}
