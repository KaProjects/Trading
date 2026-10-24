package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.PeriodContent;
import org.kaleta.persistence.entity.PeriodContentImage;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

public interface PeriodContentDao extends EntityDao<PeriodContent>
{
    List<PeriodContent> listByPeriodIds(List<Long> periodIds);

    Optional<PeriodContent> findByDate(Long periodId, Date date);

    /** Writes the text alone, leaving everything else about the entry where it is. */
    void updateContent(Long contentId, String content);

    /** What an entry's pictures look like, without reading a single byte of them. */
    List<Object[]> listImageSizes(Long contentId);

    PeriodContentImage getImage(Long imageId);

    void createImage(PeriodContentImage image);

    long countImages(Long contentId);

    /** The bytes of one image, read on their own: everywhere else they are left in the database. */
    byte[] readImageBytes(Long imageId, boolean thumbnail);

    void deleteImage(Long imageId);
}
