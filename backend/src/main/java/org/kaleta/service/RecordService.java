package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.Utils;
import org.kaleta.dao.CompanyDao;
import org.kaleta.dao.RecordDao;
import org.kaleta.dto.RecordCreateDto;
import org.kaleta.dto.RecordDto;
import org.kaleta.entity.Company;
import org.kaleta.entity.Record;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

@ApplicationScoped
public class RecordService
{
    @Inject
    RecordDao recordDao;
    @Inject
    CompanyDao companyDao;

    public List<Record> getRecords(String companyId)
    {
        return recordDao.list(companyId);
    }

    public void updateRecord(RecordDto recordDto)
    {
        Record record;
        try {
            record = recordDao.get(recordDto.getId());
        } catch (NoResultException e){
            throw new ServiceFailureException("record with id '" + recordDto.getId() + "' not found");
        }
        if (recordDto.getDate() != null) {
            if (Utils.isValidDbDate(recordDto.getDate())){
                record.setDate(Date.valueOf(recordDto.getDate()));
            } else {
                throw new ServiceFailureException("invalid date format '" + recordDto.getDate() + "' not YYYY-MM-DD");
            }
        }
        if (recordDto.getTitle() != null) record.setTitle(recordDto.getTitle());
        if (recordDto.getPrice() != null) record.setPrice(new BigDecimal(recordDto.getPrice()));
        if (recordDto.getPe() != null) record.setPe(recordDto.getPe().isBlank() ? null : new BigDecimal(recordDto.getPe()));
        if (recordDto.getPs() != null) record.setPs(recordDto.getPs().isBlank() ? null : new BigDecimal(recordDto.getPs()));
        if (recordDto.getDy() != null) record.setDy(recordDto.getDy().isBlank() ? null : new BigDecimal(recordDto.getDy()));
        if (recordDto.getTargets() != null) record.setTargets(recordDto.getTargets());
        if (recordDto.getContent() != null) record.setContent(recordDto.getContent());
        if (recordDto.getStrategy() != null) record.setStrategy(recordDto.getStrategy());

        recordDao.save(record);
    }

    public Record createRecord(RecordCreateDto recordCreateDto)
    {
        Record newRecord = new Record();
        try {
            Company company = companyDao.get(recordCreateDto.getCompanyId());
            newRecord.setCompany(company);
        } catch (NoResultException e){
            throw new ServiceFailureException("company with id '" + recordCreateDto.getCompanyId() + "' not found");
        }
        if (Utils.isValidDbDate(recordCreateDto.getDate())){
            newRecord.setDate(Date.valueOf(recordCreateDto.getDate()));
        } else {
            throw new ServiceFailureException("invalid date format '" + recordCreateDto.getDate() + "' not YYYY-MM-DD");
        }
        newRecord.setPrice(new BigDecimal(recordCreateDto.getPrice()));
        newRecord.setTitle(recordCreateDto.getTitle());

        recordDao.create(newRecord);

        return recordDao.get(newRecord.getId());
    }
}
