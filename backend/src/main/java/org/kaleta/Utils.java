package org.kaleta;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

public class Utils
{
    public static int compareDates(String dateA, String dateB)
    {
        if (dateA == null) dateA = Constants.dateFormatDto.format(new Date(0));
        if (dateB == null) dateB = Constants.dateFormatDto.format(new Date(0));
        String[] splitDateA = dateA.split("\\.");
        String[] splitDateB = dateB.split("\\.");
        for (int i=2; i >= 0; i--)
        {
            int compare = Integer.parseInt(splitDateA[i]) - Integer.parseInt(splitDateB[i]);
            if (compare != 0) return compare;
        }
        return 0;
    }

    public static String format(BigDecimal value)
    {
        if (value == null){
            return "";
        } else {
            return value.stripTrailingZeros().toPlainString();
        }
    }

    public static BigDecimal computeProfit(BigDecimal before, BigDecimal now)
    {
        if (format(before).equals("0")) return null;
        return now.divide(before, 4, RoundingMode.HALF_UP).subtract(new BigDecimal(1)).multiply(new BigDecimal(100));
    }
}
