package org.kaleta.rest.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SentimentOutperformerDto
{
    private String ticker;
    private int articleCount;
    private int positiveCount;
    private int neutralCount;
    private int negativeCount;
    private int weightedSentimentSum;
    private BigDecimal sentimentScore;
}
