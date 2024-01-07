package org.kaleta;

public class Utils
{
    public static int compareDates(String dateA, String dateB)
    {
        String[] splitDateA = dateA.split("\\.");
        String[] splitDateB = dateB.split("\\.");
        for (int i=2; i >= 0; i--)
        {
            int compare = Integer.parseInt(splitDateA[i]) - Integer.parseInt(splitDateB[i]);
            if (compare != 0) return compare;
        }
        return 0;
    }
}
