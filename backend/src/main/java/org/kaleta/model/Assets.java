package org.kaleta.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Assets
{
    private List<Asset> assets = new ArrayList<>();
    private Asset aggregate;
}
