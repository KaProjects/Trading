package org.kaleta.persistence.impl;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;
import jakarta.transaction.Transactional;
import org.kaleta.persistence.api.PeriodContentDao;
import org.kaleta.persistence.entity.PeriodContent;
import org.kaleta.persistence.entity.PeriodContentImage;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PeriodContentDaoImpl extends EntityDaoImpl<PeriodContent> implements PeriodContentDao
{
    @Override
    protected Class<PeriodContent> getEntityClass()
    {
        return PeriodContent.class;
    }

    @Override
    public List<PeriodContent> listByPeriodIds(List<Long> periodIds)
    {
        if (periodIds.isEmpty()) return List.of();

        return entityManager.createQuery(
                        "SELECT DISTINCT c FROM PeriodContent c LEFT JOIN FETCH c.images "
                                + "WHERE c.period.id IN :periodIds ORDER BY c.date, c.id",
                        PeriodContent.class)
                .setParameter("periodIds", periodIds)
                .getResultList();
    }

    @Override
    public Optional<PeriodContent> findByDate(Long periodId, Date date)
    {
        try {
            return Optional.of(entityManager.createQuery(
                            selectQuery + "WHERE t.period.id=:periodId AND t.date=:date",
                            PeriodContent.class)
                    .setParameter("periodId", periodId)
                    .setParameter("date", date)
                    .getSingleResult());
        } catch (NoResultException exception) {
            return Optional.empty();
        }
    }

    @Transactional
    @Override
    public void updateContent(Long contentId, String content)
    {
        PeriodContent managed = entityManager.find(PeriodContent.class, contentId);
        if (managed != null) {
            managed.setContent(content);
        }
    }

    @Override
    public List<Object[]> listImageSizes(Long contentId)
    {
        return entityManager.createQuery(
                        "SELECT i.id, i.width, i.height FROM PeriodContentImage i "
                                + "WHERE i.content.id=:contentId ORDER BY i.id DESC", Object[].class)
                .setParameter("contentId", contentId)
                .getResultList();
    }

    @Override
    public PeriodContentImage getImage(Long imageId)
    {
        return entityManager.createQuery(
                        "SELECT i FROM PeriodContentImage i WHERE i.id=:id", PeriodContentImage.class)
                .setParameter("id", imageId)
                .getSingleResult();
    }

    @Transactional
    @Override
    public void createImage(PeriodContentImage image)
    {
        entityManager.persist(image);
    }

    @Override
    public long countImages(Long contentId)
    {
        return entityManager.createQuery(
                        "SELECT COUNT(i) FROM PeriodContentImage i WHERE i.content.id=:contentId",
                        Long.class)
                .setParameter("contentId", contentId)
                .getSingleResult();
    }

    @Override
    public byte[] readImageBytes(Long imageId, boolean thumbnail)
    {
        return entityManager.createQuery(
                        "SELECT " + (thumbnail ? "i.thumbnail" : "i.image")
                                + " FROM PeriodContentImage i WHERE i.id=:id", byte[].class)
                .setParameter("id", imageId)
                .getSingleResult();
    }

    @Transactional
    @Override
    public void deleteImage(Long imageId)
    {
        PeriodContentImage managed = entityManager.find(PeriodContentImage.class, imageId);
        if (managed != null) {
            entityManager.remove(managed);
        }
    }
}
