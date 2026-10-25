package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.CaseEntry;
import org.kaleta.persistence.entity.CaseEntryImage;

import java.sql.Date;
import java.util.List;
import java.util.Optional;

public interface CaseEntryDao extends EntityDao<CaseEntry>
{
    /** Every day written into the case, oldest first, with the pictures each one holds. */
    List<CaseEntry> listByCaseId(Long caseId);

    Optional<CaseEntry> findByDate(Long caseId, Date date);

    /** Writes the text alone, leaving the pictures where they are. */
    void updateContent(Long entryId, String content);

    List<Object[]> listImageSizes(Long entryId);

    CaseEntryImage getImage(Long imageId);

    void createImage(CaseEntryImage image);

    long countImages(Long entryId);

    byte[] readImageBytes(Long imageId, boolean thumbnail);

    void deleteImage(Long imageId);
}
