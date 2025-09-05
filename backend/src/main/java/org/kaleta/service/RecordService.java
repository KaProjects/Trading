package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.dao.RecordDao;
import org.kaleta.dto.RecordCreateDto;
import org.kaleta.dto.RecordDto;
import org.kaleta.entity.Record;
import org.kaleta.model.RecordsModel;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Deprecated
@ApplicationScoped
public class RecordService
{
    @Inject
    RecordDao recordDao;
    @Inject
    CompanyService companyService;
    @Inject
    ConvertService convertService;

    public void updateRecord(RecordDto dto)
    {
        Record record;
        try {
            record = recordDao.get(dto.getId());
        } catch (NoResultException e){
            throw new ServiceFailureException("record with id '" + dto.getId() + "' not found");
        }

        if (dto.getDate() != null) record.setDate(convertService.parseDate(dto.getDate()));
        if (dto.getTitle() != null) record.setTitle(dto.getTitle());
        if (dto.getPrice() != null) record.setPrice(new BigDecimal(dto.getPrice()));
        if (dto.getPe() != null) record.setPe(dto.getPe().isBlank() ? null : new BigDecimal(dto.getPe()));
        if (dto.getPs() != null) record.setPs(dto.getPs().isBlank() ? null : new BigDecimal(dto.getPs()));
        if (dto.getDy() != null) record.setDy(dto.getDy().isBlank() ? null : new BigDecimal(dto.getDy()));
        if (dto.getTargets() != null) record.setTargets(dto.getTargets());
        if (dto.getContent() != null) record.setContent(dto.getContent());
        if (dto.getStrategy() != null) record.setStrategy(dto.getStrategy());

        recordDao.save(record);
    }

    public Record createRecord(RecordCreateDto dto)
    {
        Record newRecord = new Record();

        newRecord.setCompany(companyService.getCompany(dto.getCompanyId()));
        newRecord.setDate(convertService.parseDate(dto.getDate()));
        newRecord.setPrice(new BigDecimal(dto.getPrice()));
        newRecord.setTitle(dto.getTitle());

        recordDao.create(newRecord);

        return recordDao.get(newRecord.getId());
    }

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
