package org.kaleta.model;

import lombok.Data;
import org.kaleta.Utils;
import org.kaleta.entity.Record;

import java.sql.Date;
import java.util.List;

public class CompanyRecordsModel
{
    private final List<Record> records;
    public CompanyRecordsModel(List<Record> records) {this.records = records;}

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
            if (record.getPe() != null) {
                return new Latest(record.getPe(), record.getDate());
            }
        }
        return null;
    }

    public Latest getLatestPs()
    {
        for (Record record : this.getSortedRecords()) {
            if (record.getPs() != null) {
                return new Latest(record.getPs(), record.getDate());
            }
        }
        return null;
    }

    public Latest getLatestDy()
    {
        for (Record record : this.getSortedRecords()) {
            if (record.getDy() != null) {
                return new Latest(record.getDy(), record.getDate());
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
        this.records.sort((recordA, recordB) -> -Utils.compareDbDates(recordA.getDate(), recordB.getDate()));
        return records;
    }
}
