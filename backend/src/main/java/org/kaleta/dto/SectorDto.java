package org.kaleta.dto;

import lombok.Data;
import org.kaleta.persistence.entity.Sector;

@Data
public class SectorDto
{
    private String key;
    private String name;

    public SectorDto(){}
    public SectorDto(String key, String name) {this.key = key;this.name = name;}

    public static int compare(SectorDto o1, SectorDto o2)
    {
        if (o1 == null && o2 == null){
            return 0;
        } else {
            if (o1 == null) return 1;
            if (o2 == null) return -1;
        }
        return o1.getName().compareTo(o2.getName());
    }

    @Deprecated
    public static SectorDto from(Sector sector)
    {
        if (sector == null) return null;
        SectorDto dto = new SectorDto();
        dto.setKey(sector.toString());
        dto.setName(sector.getName());
        return dto;
    }
}
