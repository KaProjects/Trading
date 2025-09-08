package org.kaleta.persistence.entity;

import java.math.BigDecimal;

public enum Currency
{
    $, €, £, K;

    public BigDecimal toUsd()
    {
        switch (this)
        {
            case $: return new BigDecimal(1);
            case €: return new BigDecimal("1.1");
            case £: return new BigDecimal("1.3");
            case K: return new BigDecimal("0.043");
            default: throw new IllegalStateException("unknown rate for '" + this + "'");
        }
    }
}
