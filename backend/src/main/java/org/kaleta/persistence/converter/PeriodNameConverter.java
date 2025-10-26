package org.kaleta.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.kaleta.persistence.entity.PeriodName;

@Converter(autoApply = true)
public class PeriodNameConverter implements AttributeConverter<PeriodName, String>
{
    @Override
    public String convertToDatabaseColumn(PeriodName attribute) {
        if (attribute == null) return null;
        return attribute.toString();
    }

    @Override
    public PeriodName convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return PeriodName.valueOf(dbData);
    }
}