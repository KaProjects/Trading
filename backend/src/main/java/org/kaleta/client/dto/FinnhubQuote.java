package org.kaleta.client.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;

@Data
@RegisterForReflection
public class FinnhubQuote
{
    private String c;
    private String d;
    private String dp;
    private String h;
    private String l;
    private String o;
    private String pc;
    private String t;
}
