package org.kaleta.rest.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OutperformersDto
{
    private List<EpsOutperformerDto> epsEstimates = new ArrayList<>();
    private List<DisqualifiedCompanyDto> epsEstimatesDisqualified = new ArrayList<>();

    private List<MarginOutperformerDto> margins = new ArrayList<>();
    private List<DisqualifiedCompanyDto> marginsDisqualified = new ArrayList<>();

    private List<SentimentOutperformerDto> sentiment = new ArrayList<>();
    private List<DisqualifiedCompanyDto> sentimentDisqualified = new ArrayList<>();

    private List<TargetOutperformerDto> targets = new ArrayList<>();
    private List<DisqualifiedCompanyDto> targetsDisqualified = new ArrayList<>();
}
