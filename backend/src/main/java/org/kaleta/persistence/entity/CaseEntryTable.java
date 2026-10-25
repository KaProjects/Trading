package org.kaleta.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * A table written into a day of a case - companies across the top, a metric down the side, and
 * a typed value with a rating in each cell.
 *
 * The grid is held as JSON rather than as rows and cells of its own: it is typed by hand, read
 * as a whole and never queried across, so a blob is the shape it actually has.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Entity
@Table(name = "CaseEntryTable")
public class CaseEntryTable extends AbstractEntity
{
    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "title")
    private String title;

    @Column(name = "data", nullable = false)
    private String data;

    @ManyToOne
    @JoinColumn(name = "entryId", nullable = false)
    private CaseEntry entry;
}
