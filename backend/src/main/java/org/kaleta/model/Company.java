package org.kaleta.model;

import lombok.Data;
import org.kaleta.persistence.entity.Currency;

@Data
public class Company implements Comparable<Company>
{
    private String id;
    private String ticker;
    private Currency currency;
    private Boolean watching;
    private Sector sector;

    public Company() {}

    @Override
    public int compareTo(Company other) {return this.getTicker().compareTo(other.getTicker());}

    @Data
    public static class Sector implements Comparable<Sector>
    {
        private String key;
        private String name;

        public Sector(){}
        public Sector(org.kaleta.persistence.entity.Sector sector) {
            this.key = sector.toString();
            this.name = sector.getName();
        }

        @Override
        public int compareTo(Sector other) {
            if (other == null) return -1;
            return this.getKey().compareTo(other.getKey());
        }
    }
}
