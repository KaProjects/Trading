package org.kaleta.persistence.converter;


import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.YearMonth;

@Converter(autoApply = true)
public class YearMonthConverter implements AttributeConverter<YearMonth, String>
{

    @Override
    public String convertToDatabaseColumn(YearMonth attribute) {
        if (attribute == null) return null;
        String stringValue = attribute.toString();
        return stringValue.substring(2,4) + stringValue.substring(5,7);
    }

    @Override
    public YearMonth convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return YearMonth.parse("20" + dbData.substring(0,2) + "-" + dbData.substring(2,4));
    }
}
