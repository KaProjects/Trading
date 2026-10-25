package org.kaleta.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * Research written for a period on one day. The date is the identity: writing again for a date
 * the period already has fills that entry in rather than adding a second one, which is why the
 * pair is unique. An entry is never deleted - emptied of its text and its images, it simply
 * stops being shown.
 */
@Data
@EqualsAndHashCode(callSuper = true, exclude = "images")
@ToString(callSuper = true, exclude = "images")
@Entity
@Table(
        name = "PeriodContent",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_periodContentDate",
                columnNames = {"periodId", "date"}))
public class PeriodContent extends AbstractEntity
{
    @Column(name = "date", nullable = false)
    private Date date;

    @Column(name = "content")
    private String content;

    @ManyToOne
    @JoinColumn(name = "periodId", nullable = false)
    private Period period;

    /**
     * Read only: pictures are written and removed one at a time through their own calls. They
     * were cascaded from here once, which quietly deleted every picture of an entry whenever its
     * text was saved - merging a detached entry carries an empty collection with it.
     */
    @OneToMany(mappedBy = "content")
    // Newest first: the picture just added is the one being looked at.
    @OrderBy("id DESC")
    private List<PeriodContentImage> images = new ArrayList<>();
}
