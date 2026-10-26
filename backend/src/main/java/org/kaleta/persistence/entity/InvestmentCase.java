package org.kaleta.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

    @ElementCollection
    @CollectionTable(name = "CaseCompany", joinColumns = @JoinColumn(name = "caseId"))
    @Column(name = "companyId", nullable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<Long> companyIds = new ArrayList<>();

    /**
     * Companies the app does not hold, kept as the names they were written as. A case covers
     * what it covers whether or not the app knows the company yet; once it does, the name is
     * replaced by the real one.
     */
    @ElementCollection
    @CollectionTable(name = "CaseCompanyName", joinColumns = @JoinColumn(name = "caseId"))
    @Column(name = "value", nullable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private List<String> companyNames = new ArrayList<>();
}
