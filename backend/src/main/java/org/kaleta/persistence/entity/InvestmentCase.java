package org.kaleta.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/**
 * An investment case: a thesis followed over time, across however many companies it covers.
 *
 * A case is open until the day it is closed, which is all "state" amounts to here. It is named
 * InvestmentCase rather than Case because "case" is a keyword in both SQL and JPQL, and a table
 * that has to be quoted everywhere it is read is a trap waiting for the next native query.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Entity
@Table(name = "InvestmentCase")
public class InvestmentCase extends AbstractEntity
{
    @Column(name = "name", nullable = false)
    private String name;

    /** The day the case was opened. Settable: a case is often written down after it was made. */
    @Column(name = "createdAt", nullable = false)
    private Date createdAt;

    /** The day the case was closed, or null while it is still open. */
    @Column(name = "closedAt")
    private Date closedAt;

    /**
     * The companies the case covers, each with the day it was taken into the case: a case grows
     * as it is thought about, and what it is answerable for in a company starts on the day that
     * company joined it rather than on the day the case was opened.
     */
    @ElementCollection
    @CollectionTable(name = "CaseCompany", joinColumns = @JoinColumn(name = "caseId"))
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<CaseCompany> companies = new ArrayList<>();

    /**
     * Companies the app does not hold, kept as the names they were written as. A case covers
     * what it covers whether or not the app knows the company yet; once it does, the name is
     * replaced by the real one.
     */
    @ElementCollection
    @CollectionTable(name = "CaseCompanyName", joinColumns = @JoinColumn(name = "caseId"))
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<CaseCompanyName> companyNames = new ArrayList<>();

    /** A company of the app, in the case since the day it was added. */
    @Data
    @Embeddable
    public static class CaseCompany
    {
        @Column(name = "companyId", nullable = false)
        private Long companyId;

        @Column(name = "addedAt", nullable = false)
        private Date addedAt;
    }

    /** A company the app does not hold, in the case since the day it was written down. */
    @Data
    @Embeddable
    public static class CaseCompanyName
    {
        @Column(name = "value", nullable = false)
        private String value;

        @Column(name = "addedAt", nullable = false)
        private Date addedAt;
    }
}
