package org.kaleta.analyzer.cci;


import lombok.Data;

import java.math.BigDecimal;

@Data
public class DataCCI {
    // time,open,high,low,close,Volume,Volume MA,Histogram,MACD,Signal,CCI

    private Long id;

    private String time;

    private BigDecimal close;

    private BigDecimal cci;
}
