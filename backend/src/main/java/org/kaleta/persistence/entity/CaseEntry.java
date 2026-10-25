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
 * A day written into a case. Like a period's research, the date is the identity - a case holds
 * one entry per day, and writing that day again fills it in rather than adding a second one.
 */
@Data
@EqualsAndHashCode(callSuper = true, exclude = "images")
@ToString(callSuper = true, exclude = "images")
@Entity
@Table(
        name = "CaseEntry",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_caseEntryDate",
                columnNames = {"caseId", "date"}))
public class CaseEntry extends AbstractEntity
{
    @Column(name = "date", nullable = false)
    private Date date;

    @Column(name = "content")
    private String content;

    @ManyToOne
    @JoinColumn(name = "caseId", nullable = false)
    private InvestmentCase investmentCase;

    /** Read only, as with a period's pictures: they are written through their own calls. */
    @OneToMany(mappedBy = "entry")
    @OrderBy("id DESC")
    private List<CaseEntryImage> images = new ArrayList<>();
}
