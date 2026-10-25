package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.Utils;
import org.kaleta.persistence.api.CaseDao;
import org.kaleta.persistence.api.CaseEntryDao;
import org.kaleta.persistence.entity.CaseEntry;
import org.kaleta.persistence.entity.CaseEntryImage;
import org.kaleta.persistence.entity.InvestmentCase;
import org.kaleta.rest.dto.CaseEntryDto;
import org.kaleta.rest.dto.CaseEntryUpdateDto;
import org.kaleta.rest.error.InvalidInputException;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The days written into a case, one entry per date.
 *
 * A case is kept forwards: a new day is always today, and the only other date that can be
 * written is one the case already has. That is what makes the date an identity here as well -
 * there is no way to end up with two entries for one day, and no way to invent a past one.
 */
@ApplicationScoped
public class CaseEntryService
{
    public static final int MAX_IMAGES_PER_ENTRY = 10;

    @Inject
    CaseEntryDao caseEntryDao;
    @Inject
    CaseDao caseDao;
    @Inject
    ImageService imageService;

    /** Everything written into the case, oldest first; an empty day is left out. */
    public List<CaseEntryDto> getByCaseId(Long caseId)
    {
        return caseEntryDao.listByCaseId(caseId).stream()
                .filter(entry -> !isEmpty(entry))
                .map(CaseEntryService::toDto)
                .toList();
    }

    public CaseEntryDto save(Long caseId, CaseEntryUpdateDto dto)
    {
        InvestmentCase investmentCase = investmentCase(caseId);
        LocalDate date = parseDate(dto.getDate());

        Long entryId = caseEntryDao.findByDate(caseId, Date.valueOf(date))
                .map(existing -> {
                    caseEntryDao.updateContent(existing.getId(), dto.getContent());
                    return existing.getId();
                })
                .orElseGet(() -> {
                    requireToday(date);
                    CaseEntry created = new CaseEntry();
                    created.setInvestmentCase(investmentCase);
                    created.setDate(Date.valueOf(date));
                    created.setContent(dto.getContent());
                    caseEntryDao.create(created);
                    return created.getId();
                });

        // Answered from what was written rather than read back, as with a period's research.
        return new CaseEntryDto(entryId, date.toString(), dto.getContent(), images(entryId));
    }

    public CaseEntryDto.Image addImage(Long entryId, byte[] uploaded, String mime)
    {
        CaseEntry entry = entry(entryId);
        if (caseEntryDao.countImages(entryId) >= MAX_IMAGES_PER_ENTRY) {
            throw new InvalidInputException("an entry can hold at most "
                    + MAX_IMAGES_PER_ENTRY + " images");
        }

        ImageService.Prepared prepared = imageService.prepare(uploaded, mime);
        CaseEntryImage image = new CaseEntryImage();
        image.setEntry(entry);
        image.setMime(prepared.mime());
        image.setWidth(prepared.width());
        image.setHeight(prepared.height());
        image.setImage(prepared.image());
        image.setThumbnail(prepared.thumbnail());
        image.setUploadedAt(LocalDateTime.now());

        caseEntryDao.createImage(image);
        return new CaseEntryDto.Image(image.getId(), image.getWidth(), image.getHeight());
    }

    public void deleteImage(Long imageId)
    {
        image(imageId);
        caseEntryDao.deleteImage(imageId);
    }

    public PeriodContentService.Picture readImage(Long imageId, boolean thumbnail)
    {
        CaseEntryImage image = image(imageId);
        return new PeriodContentService.Picture(
                image.getMime(), caseEntryDao.readImageBytes(imageId, thumbnail));
    }

    private static void requireToday(LocalDate date)
    {
        if (!date.equals(LocalDate.now())) {
            throw new InvalidInputException("a new case entry is written for today ('"
                    + LocalDate.now() + "'), not '" + date + "'");
        }
    }

    private static boolean isEmpty(CaseEntry entry)
    {
        return entry.getImages().isEmpty() && Utils.isBlankContent(entry.getContent());
    }

    private List<CaseEntryDto.Image> images(Long entryId)
    {
        return caseEntryDao.listImageSizes(entryId).stream()
                .map(row -> new CaseEntryDto.Image(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).intValue(),
                        ((Number) row[2]).intValue()))
                .toList();
    }

    private static CaseEntryDto toDto(CaseEntry entry)
    {
        return new CaseEntryDto(
                entry.getId(),
                entry.getDate().toString(),
                entry.getContent(),
                entry.getImages().stream()
                        .map(image -> new CaseEntryDto.Image(
                                image.getId(), image.getWidth(), image.getHeight()))
                        .toList());
    }

    private static LocalDate parseDate(String value)
    {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw new InvalidInputException("date '" + value + "' must match YYYY-MM-DD");
        }
    }

    private InvestmentCase investmentCase(Long caseId)
    {
        try {
            return caseDao.get(caseId);
        } catch (NoResultException exception) {
            throw new InvalidInputException("case with id '" + caseId + "' not found");
        }
    }

    private CaseEntry entry(Long entryId)
    {
        try {
            return caseEntryDao.get(entryId);
        } catch (NoResultException exception) {
            throw new InvalidInputException("case entry with id '" + entryId + "' not found");
        }
    }

    private CaseEntryImage image(Long imageId)
    {
        try {
            return caseEntryDao.getImage(imageId);
        } catch (NoResultException exception) {
            throw new InvalidInputException("image with id '" + imageId + "' not found");
        }
    }
}
