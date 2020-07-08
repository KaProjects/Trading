package org.kaleta.analyzer;

import org.kaleta.analyzer.cci.DataCCI;

import java.io.IOException;
import java.util.List;
public class Initializer {

    public static void main(String[] args) throws IOException {

        List<DataCCI> data = new DataFileReader().readDataV1("AAL60.csv");

        MonteCarloSimulator.cci(data,
                -25, -125, 200,
                25, 125, 200);
    }
}
