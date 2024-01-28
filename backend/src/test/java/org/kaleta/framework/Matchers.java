package org.kaleta.framework;

import org.hamcrest.Matcher;
import org.kaleta.dto.RecordsUiCompanyListsDto;

import static org.hamcrest.Matchers.equalTo;

public class Matchers
{
    public static Matcher<RecordsUiCompanyListsDto.Company> hasTicker(String ticker){
        return org.hamcrest.Matchers.hasProperty("ticker", equalTo(ticker));
    }
}
