package org.kaleta.analyzer;

import org.kaleta.analyzer.cci.DataCCI;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class DataFileReader {


    // time,open,high,low,close,Volume,Volume MA,Histogram,MACD,Signal,CCI
    public List<DataCCI> readDataV1(String fileName) throws IOException {
        String pathToFile = DataFileReader.class.getClassLoader().getResource(fileName).getFile();
        BufferedReader csvReader = new BufferedReader(new FileReader(pathToFile));

        List<DataCCI> outputData = new ArrayList<DataCCI>();
        String row;
        long id = 0;
        while ((row = csvReader.readLine()) != null) {
            if (row.contains("time")) continue; // first line

            String[] dataArray = row.split(",");

            DataCCI data = new DataCCI();
            data.setId(id);
            data.setTime(dataArray[0]);
            data.setClose(new BigDecimal(dataArray[4]));
            data.setCci(new BigDecimal(dataArray[10]));
            outputData.add(data);

            id++;
        }
        csvReader.close();
        return outputData;
    }
}
