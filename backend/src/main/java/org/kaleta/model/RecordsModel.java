package org.kaleta.model;

import lombok.Data;
import org.kaleta.Utils;
import org.kaleta.persistence.entity.Record;

import java.sql.Date;
import java.util.List;

@Deprecated
public class RecordsModel
{
    private final List<Record> records;
    public RecordsModel(List<Record> records) {this.records = records;}

    @Data
    public static class Latest{
        private Object value;
        private Date date;
        public Latest(Object value, Date date) {this.value = value;this.date = date;}
    }

    public Latest getLatestPrice()
    {
        for (Record record : this.getSortedRecords()) {
            if (record.getPrice() != null) {
                return new Latest(record.getPrice(), record.getDate());
            }
        }
        return null;
    }

    public Latest getLatestPe()
    {
        for (Record record : this.getSortedRecords()) {
            if (record.getPriceToNetIncome() != null) {
                return new Latest(record.getPriceToNetIncome(), record.getDate());
            }
        }
        return null;
    }

    public Latest getLatestPs()
    {
        for (Record record : this.getSortedRecords()) {
            if (record.getPriceToRevenues() != null) {
                return new Latest(record.getPriceToRevenues(), record.getDate());
            }
        }
        return null;
    }

    public Latest getLatestDy()
    {
        for (Record record : this.getSortedRecords()) {
            if (record.getDividendYield() != null) {
                return new Latest(record.getDividendYield(), record.getDate());
            }
        }
        return null;
    }

    public Latest getLatestTargets()
    {
        for (Record record : this.getSortedRecords()) {
            if (record.getTargets() != null) {
                return new Latest(record.getTargets(), record.getDate());
            }
        }
        return null;
    }

    public Latest getLatestStrategy()
    {
        for (Record record : this.getSortedRecords()) {
            if (record.getStrategy() != null) {
                return new Latest(record.getStrategy(), record.getDate());
            }
        }
        return null;
    }

    public List<Record> getSortedRecords()
    {
        records.sort((recordA, recordB) -> -Utils.compareDbDates(recordA.getDate(), recordB.getDate()));
        return records;
    }
}
