package org.kaleta.analyzer;

import org.kaleta.analyzer.cci.ConditionerCCI;
import org.kaleta.analyzer.cci.DataCCI;
import org.kaleta.analyzer.cci.TraderCCI;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
public class Initializer {

    public static void main(String[] args) throws IOException {
//        String pathToFile = DataFileReader.class.getClassLoader().getResource("AAL60.csv").getFile();
//
//        List<DataCCI> data = new DataFileReader().readDataV1(args[0] == null ? pathToFile : args[0]);
//
//        MonteCarloSimulator.cci(data,
//                -80, -110, 20,50,
//                80, 110, 20,50);

        TraderCCI traderALL = new TraderCCI(new DataFileReader().readDataV1(DataFileReader.class.getClassLoader().getResource("BA60v2.csv").getFile()),
                new ConditionerCCI(new BigDecimal("-150"), new BigDecimal("-100"),new BigDecimal("150"), new BigDecimal("100")));
        traderALL.printTradesWithCapital(new BigDecimal("1000"));
    }
}
