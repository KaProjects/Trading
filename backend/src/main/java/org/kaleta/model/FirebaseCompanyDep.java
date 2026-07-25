package org.kaleta.model;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class FirebaseCompanyDep
{
    private String cci;
    private String diff;
    private String macd;
    private String price;
    private String signal;
    private String ticker;
    private String time;
}
