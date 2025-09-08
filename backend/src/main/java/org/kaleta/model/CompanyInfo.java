package org.kaleta.model;

import lombok.Data;
import org.kaleta.persistence.entity.Sector;

import java.sql.Date;

@Data
public class CompanyInfo
{
    private String id;
    private String ticker;
    private boolean watching;
    private Sector sector;
    private Date latestReviewDate;
    private Date latestPurchaseDate; //only owned
    private Date latestStrategyDate;
}
