package org.kaleta.persistence.api;

import org.kaleta.persistence.entity.CaseEntry;
import org.kaleta.persistence.entity.CaseEntryImage;
import org.kaleta.persistence.entity.CaseEntryTable;

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

    /** Moves the entry to another day, which is how a day written down late is put right. */
    void updateDate(Long entryId, Date date);

    List<Object[]> listImageSizes(Long entryId);

    CaseEntryImage getImage(Long imageId);

    void createImage(CaseEntryImage image);

    long countImages(Long entryId);

    byte[] readImageBytes(Long imageId, boolean thumbnail);

    void deleteImage(Long imageId);

    List<CaseEntryTable> listTables(List<Long> entryIds);

    CaseEntryTable getTable(Long tableId);

    void createTable(CaseEntryTable table);

    /** Writes the grid of a table that is already there. */
    void updateTable(Long tableId, String title, String data);

    long countTables(Long entryId);

    void deleteTable(Long tableId);
}
