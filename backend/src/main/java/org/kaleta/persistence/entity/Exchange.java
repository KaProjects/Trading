package org.kaleta.persistence.entity;

import lombok.Getter;

@Getter
public enum Exchange
{
    XAMS("Euronext Amsterdam", "EURONEXT", null, false),
    XETR("Xetra", "XETR", "ETR", false),
    XLON("London Stock Exchange", "LSE", "LON", false),
    XNAS("Nasdaq", "NASDAQ", "NASDAQ", true),
    XNYS("New York Stock Exchange", "NYSE", "NYSE", true),
    XPAR("Euronext Paris", "EURONEXT", "EPA", false),
    XPRA("Prague Stock Exchange", "PSECZ", null, false),
    XSWX("SIX Swiss Exchange", "SIX", null, false),
    XTSE("Toronto Stock Exchange", "TSX", "TSE", false),
    ;

    private final String name;
    private final String tradingViewCode;
    private final String marketBeatCode;
    private final boolean zacksSupported;

    Exchange(String name, String tradingViewCode, String marketBeatCode, boolean zacksSupported)
    {
        this.name = name;
        this.tradingViewCode = tradingViewCode;
        this.marketBeatCode = marketBeatCode;
        this.zacksSupported = zacksSupported;
    }
}
