package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.Utils;

import java.sql.Date;

@ApplicationScoped
public class CommonService
{
    Date getDbDate(String date)
    {
        if (Utils.isValidDbDate(date)){
            return Date.valueOf(date);
        } else {
            throw new ServiceFailureException("invalid date format '" + date + "' not YYYY-MM-DD");
        }
    }
}
