package org.kaleta.rest.dto;

import lombok.Data;
import org.kaleta.dto.CompanyDto;
import org.kaleta.model.Assets;
import org.kaleta.model.Periods;
import org.kaleta.model.PriceIndicators;
import org.kaleta.model.Record;
import org.kaleta.persistence.entity.Latest;

import java.util.ArrayList;
import java.util.List;

@Data
public class ResearchDto
{
    private CompanyDto company;
    private Periods periods;
    private List<Record> records = new ArrayList<>();
    private Latest latest;
    private PriceIndicators indicators;
    private Assets assets;
}
