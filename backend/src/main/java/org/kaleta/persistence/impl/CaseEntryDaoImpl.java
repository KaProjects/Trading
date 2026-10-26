package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.kaleta.persistence.api.CaseEntryDao;
import org.kaleta.persistence.entity.CaseEntry;
import org.kaleta.persistence.entity.CaseEntryImage;
import org.kaleta.persistence.entity.CaseEntryTable;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class CaseEntryDaoImpl extends EntityDaoImpl<CaseEntry> implements CaseEntryDao
{
    @Override
    protected Class<CaseEntry> getEntityClass()
    {
        return CaseEntry.class;
    }

    @Override
    public List<CaseEntry> listByCaseId(Long caseId)
    {
        return entityManager.createQuery(
                        "SELECT DISTINCT e FROM CaseEntry e LEFT JOIN FETCH e.images "
                                + "WHERE e.investmentCase.id=:caseId ORDER BY e.date, e.id",
                        CaseEntry.class)
                .setParameter("caseId", caseId)
                .getResultList();
    }

    @Override
    public Optional<CaseEntry> findByDate(Long caseId, Date date)
    {
        try {
            return Optional.of(entityManager.createQuery(
                            selectQuery + "WHERE t.investmentCase.id=:caseId AND t.date=:date",
                            CaseEntry.class)
                    .setParameter("caseId", caseId)
                    .setParameter("date", date)
                    .getSingleResult());
        } catch (NoResultException exception) {
            return Optional.empty();
        }
    }

    @Transactional
    @Override
    public void updateContent(Long entryId, String content)
    {
        CaseEntry managed = entityManager.find(CaseEntry.class, entryId);
        if (managed != null) {
            managed.setContent(content);
        }
    }

    @Transactional
    @Override
    public void updateDate(Long entryId, Date date)
    {
        CaseEntry managed = entityManager.find(CaseEntry.class, entryId);
        if (managed != null) {
            managed.setDate(date);
        }
    }

    @Override
    public List<Object[]> listImageSizes(Long entryId)
    {
        return entityManager.createQuery(
                        "SELECT i.id, i.width, i.height FROM CaseEntryImage i "
                                + "WHERE i.entry.id=:entryId ORDER BY i.id DESC", Object[].class)
                .setParameter("entryId", entryId)
                .getResultList();
    }

    @Override
    public CaseEntryImage getImage(Long imageId)
    {
        return entityManager.createQuery(
                        "SELECT i FROM CaseEntryImage i WHERE i.id=:id", CaseEntryImage.class)
                .setParameter("id", imageId)
                .getSingleResult();
    }

    @Transactional
    @Override
    public void createImage(CaseEntryImage image)
    {
        entityManager.persist(image);
    }

    @Override
    public long countImages(Long entryId)
    {
        return entityManager.createQuery(
                        "SELECT COUNT(i) FROM CaseEntryImage i WHERE i.entry.id=:entryId", Long.class)
                .setParameter("entryId", entryId)
                .getSingleResult();
    }

    @Override
    public byte[] readImageBytes(Long imageId, boolean thumbnail)
    {
        return entityManager.createQuery(
                        "SELECT " + (thumbnail ? "i.thumbnail" : "i.image")
                                + " FROM CaseEntryImage i WHERE i.id=:id", byte[].class)
                .setParameter("id", imageId)
                .getSingleResult();
    }

    @Transactional
    @Override
    public void deleteImage(Long imageId)
    {
        CaseEntryImage managed = entityManager.find(CaseEntryImage.class, imageId);
        if (managed != null) {
            entityManager.remove(managed);
        }
    }

    @Override
    public List<CaseEntryTable> listTables(List<Long> entryIds)
    {
        if (entryIds.isEmpty()) return List.of();

        return entityManager.createQuery(
                        "SELECT t FROM CaseEntryTable t WHERE t.entry.id IN :entryIds "
                                + "ORDER BY t.position, t.id", CaseEntryTable.class)
                .setParameter("entryIds", entryIds)
                .getResultList();
    }

    @Override
    public CaseEntryTable getTable(Long tableId)
    {
        return entityManager.createQuery(
                        "SELECT t FROM CaseEntryTable t WHERE t.id=:id", CaseEntryTable.class)
                .setParameter("id", tableId)
                .getSingleResult();
    }

    @Transactional
    @Override
    public void createTable(CaseEntryTable table)
    {
        entityManager.persist(table);
    }

    @Transactional
    @Override
    public void updateTable(Long tableId, String title, String data)
    {
        CaseEntryTable managed = entityManager.find(CaseEntryTable.class, tableId);
        if (managed != null) {
            managed.setTitle(title);
            managed.setData(data);
        }
    }

    @Override
    public long countTables(Long entryId)
    {
        return entityManager.createQuery(
                        "SELECT COUNT(t) FROM CaseEntryTable t WHERE t.entry.id=:entryId", Long.class)
                .setParameter("entryId", entryId)
                .getSingleResult();
    }

    @Transactional
    @Override
    public void deleteTable(Long tableId)
    {
        CaseEntryTable managed = entityManager.find(CaseEntryTable.class, tableId);
        if (managed != null) {
            entityManager.remove(managed);
        }
    }
}
