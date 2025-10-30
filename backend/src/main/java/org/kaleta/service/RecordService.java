package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.Utils;
import org.kaleta.model.RecordsModel;
import org.kaleta.persistence.api.RecordDao;
import org.kaleta.persistence.entity.Record;
import org.kaleta.rest.dto.RecordCreateDto;
import org.kaleta.rest.dto.RecordUpdateDto;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class RecordService
{
    @Inject
    RecordDao recordDao;
    @Inject
    CompanyService companyService;

    public void create(RecordCreateDto dto)
    {
        Record newRecord = new Record();

        newRecord.setCompany(companyService.getCompany(dto.getCompanyId()));
        newRecord.setDate(Date.valueOf(dto.getDate()));
        newRecord.setPrice(new BigDecimal(dto.getPrice()));
        newRecord.setTitle(dto.getTitle());

        newRecord.setPriceToRevenues(Utils.createNullableBigDecimal(dto.getPriceToRevenues()));
        newRecord.setPriceToGrossProfit(Utils.createNullableBigDecimal(dto.getPriceToGrossProfit()));
        newRecord.setPriceToOperatingIncome(Utils.createNullableBigDecimal(dto.getPriceToOperatingIncome()));
        newRecord.setPriceToNetIncome(Utils.createNullableBigDecimal(dto.getPriceToNetIncome()));

        newRecord.setDividendYield(Utils.createNullableBigDecimal(dto.getDividendYield()));

        newRecord.setSumAssetQuantity(Utils.createNullableBigDecimal(dto.getSumAssetQuantity()));
        newRecord.setAvgAssetPrice(Utils.createNullableBigDecimal(dto.getAvgAssetPrice()));

        recordDao.create(newRecord);
    }

    public void update(RecordUpdateDto dto)
    {
        Record record;
        try {
            record = recordDao.get(dto.getId());
        } catch (NoResultException e){
            throw new ServiceFailureException("record with id '" + dto.getId() + "' not found");
        }

        if (dto.getTitle() != null) record.setTitle(dto.getTitle());
        if (dto.getContent() != null) record.setContent(dto.getContent());
        if (dto.getStrategy() != null) record.setStrategy(dto.getStrategy());

        recordDao.save(record);
    }

    public List<Record> getBy(String companyId)
    {
        List<Record> records = recordDao.list(companyId);
        records.sort((a, b) -> -Utils.compareDbDates(a.getDate(), b.getDate()));
        return records;
    }

    public void delete(String recordId){
        try {
            recordDao.get(recordId);
        } catch (NoResultException e){
            throw new ServiceFailureException("record with id '" + recordId + "' not found");
        }
        recordDao.delete(recordId);
    }

    @Deprecated
    public RecordsModel getRecordsModel(String companyId)
    {
        return new RecordsModel(recordDao.list(companyId));
    }

    /**
     * @return aggregates map <companyId, [records count]>
     */
    public Map<String, int[]> getCompanyAggregates()
    {
        Map<String, int[]> map = new HashMap<>();
        for (Record record : recordDao.list())
        {
            String companyId = record.getCompany().getId();
            int[] aggregates = map.containsKey(companyId) ? map.get(companyId) : new int[]{0};
            aggregates[0] = aggregates[0] + 1;
            map.put(companyId, aggregates);
        }
        return map;
    }
}
