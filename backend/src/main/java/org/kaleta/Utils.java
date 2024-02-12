package org.kaleta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;


public class Utils
{
    /**
     * compares dates in format DD.MM.YYYY
     */
    public static int compareDtoDates(String dateA, String dateB)
    {
        if (dateA == null) dateA = "01.01.1990";
        if (dateB == null) dateB = "01.01.1990";
        String[] splitDateA = dateA.split("\\.");
        String[] splitDateB = dateB.split("\\.");
        for (int i=2; i >= 0; i--)
        {
            int compare = Integer.parseInt(splitDateA[i]) - Integer.parseInt(splitDateB[i]);
            if (compare != 0) return compare;
        }
        return 0;
    }

    public static int compareDbDates(Date dateA, Date dateB)
    {
        if (dateA == null) dateA = new Date(0);
        if (dateB == null) dateB = new Date(0);
        String[] splitDateA = dateA.toString().split("-");
        String[] splitDateB = dateB.toString().split("-");
        for (int i=0; i < 3; i++)
        {
            int compare = Integer.parseInt(splitDateA[i]) - Integer.parseInt(splitDateB[i]);
            if (compare != 0) return compare;
        }
        return 0;
    }

    /**
     * @return true if string date in format YYYY-MM-DD, false otherwise
     */
    public static boolean isValidDbDate(String dbDate)
    {
        return dbDate.matches("\\d\\d\\d\\d-\\d\\d-\\d\\d");
    }

    /**
     * @return java.sql.Date from string format YYYY-MM-DD
     */
    public static Date format(String dtoDate)
    {
        if (dtoDate == null) return null;
        String[] split = dtoDate.split("\\.");
        return Date.valueOf(split[2] + "-" + split[1] + "-" + split[0]);
    }

    /**
     * @return string date in format DD.MM.YYYY
     */
    public static String format(Date dbDate)
    {
        if (dbDate == null) return null;
        String[] split = dbDate.toString().split("-");
        return split[2] + "." + split[1] + "." + split[0];
    }

    public static String format(BigDecimal value)
    {
        if (value == null){
            return "";
        } else {
            return value.stripTrailingZeros().toPlainString();
        }
    }

    /**
     * @return formula: (now/before - 1) * 100
     */
    public static BigDecimal computeProfit(BigDecimal before, BigDecimal now)
    {
        if (format(before).equals("0")) return null;
        return now.divide(before, 4, RoundingMode.HALF_UP).subtract(new BigDecimal(1)).multiply(new BigDecimal(100));
    }

    /**
     * Examples:
     * 100 -> 100M
     * 1100 -> 1.10B
     *
     * @return formatted value of millions
     */
    public static String formatMillions(BigDecimal value)
    {
        if (value.compareTo(new BigDecimal(1000)) > 0) {
            return format(value.divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP)) + "B";
        } else {
            return format(value) + "M";
        }
    }
}
