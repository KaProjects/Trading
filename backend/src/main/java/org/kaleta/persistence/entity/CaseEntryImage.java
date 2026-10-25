package org.kaleta.persistence.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/** A picture attached to a day of a case; both copies are read only when one is streamed back. */
@Data
@EqualsAndHashCode(callSuper = true, exclude = {"image", "thumbnail"})
@ToString(callSuper = true, exclude = {"image", "thumbnail"})
@Entity
@Table(name = "CaseEntryImage")
public class CaseEntryImage extends AbstractEntity
{
    @Column(name = "mime", nullable = false, length = 30)
    private String mime;

    @Column(name = "width", nullable = false)
    private int width;

    @Column(name = "height", nullable = false)
    private int height;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "image", nullable = false)
    private byte[] image;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "thumbnail", nullable = false)
    private byte[] thumbnail;

    @Column(name = "uploadedAt", nullable = false)
    private LocalDateTime uploadedAt;

    @ManyToOne
    @JoinColumn(name = "entryId", nullable = false)
    private CaseEntry entry;
}
