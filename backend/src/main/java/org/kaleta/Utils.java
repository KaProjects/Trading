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

    public static int compareShares(String sharesA, String sharesB)
    {
        if (sharesA == null) sharesA = "0M";
        if (sharesB == null) sharesB = "0M";
        String sharesApower = sharesA.substring(sharesA.length() - 1);
        String sharesBpower = sharesB.substring(sharesB.length() - 1);
        if (sharesApower.equals("B") && sharesBpower.equals("M")) return -1;
        if (sharesApower.equals("M") && sharesBpower.equals("B")) return 1;
        BigDecimal sharesAvalue = new BigDecimal(sharesA.substring(0, sharesA.length() - 1));
        BigDecimal sharesBvalue = new BigDecimal(sharesB.substring(0, sharesB.length() - 1));
        return -sharesAvalue.compareTo(sharesBvalue);
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
    @Deprecated
    public static Date format(String dtoDate)
    {
        if (dtoDate == null) return null;
        String[] split = dtoDate.split("\\.");
        return Date.valueOf(split[2] + "-" + split[1] + "-" + split[0]);
    }

    /**
     * @return string date in format DD.MM.YYYY
     */
    @Deprecated
    public static String format(Date dbDate)
    {
        if (dbDate == null) return null;
        String[] split = dbDate.toString().split("-");
        return split[2] + "." + split[1] + "." + split[0];
    }

    @Deprecated
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
    @Deprecated
    public static String formatMillions(BigDecimal value)
    {
        if (value.compareTo(new BigDecimal(1000)) > 0) {
            return format(value.divide(new BigDecimal(1000), 2, RoundingMode.HALF_UP)) + "B";
        } else {
            return format(value) + "M";
        }
    }

    @Deprecated
    public static String formatNoDecimal(BigDecimal value)
    {
        if (value == null){
            return "";
        } else {
            return value.setScale(0, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        }
    }

    public static int compareEndingMonths(String aEndingMonth, String bEndingMonth)
    {
        int compare = Integer.parseInt(aEndingMonth.substring(0,2)) - Integer.parseInt(bEndingMonth.substring(0,2));
        if (compare == 0)
            compare = Integer.parseInt(aEndingMonth.substring(2,4)) - Integer.parseInt(bEndingMonth.substring(2,4));

        return compare;
    }
}
