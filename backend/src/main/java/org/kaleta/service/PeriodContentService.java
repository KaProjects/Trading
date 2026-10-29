package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.kaleta.Utils;
import org.kaleta.persistence.api.PeriodContentDao;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodContent;
import org.kaleta.persistence.entity.PeriodContentImage;
import org.kaleta.rest.dto.PeriodContentDto;
import org.kaleta.rest.dto.PeriodContentUpdateDto;
import org.kaleta.rest.error.InvalidInputException;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The research a period carries, one entry per date.
 *
 * A date is the entry's identity rather than a field on it: writing for a date the period
 * already has fills that entry in, and nothing is ever deleted. An entry left with no text and
 * no images is simply not handed back, so it disappears from the page while the row stays where
 * it was, ready to be written into again.
 */
@ApplicationScoped
public class PeriodContentService
{
    public static final int MAX_IMAGES_PER_ENTRY = 10;

    @Inject
    PeriodContentDao periodContentDao;
    @Inject
    PeriodDao periodDao;
    @Inject
    PeriodDateWindowService periodDateWindowService;
    @Inject
    ImageService imageService;

    /** The entries of several periods at once, as the research page needs them. */
    public Map<Long, List<PeriodContentDto>> getByPeriodIds(List<Long> periodIds)
    {
        Map<Long, List<PeriodContentDto>> byPeriod = new LinkedHashMap<>();
        for (PeriodContent content : periodContentDao.listByPeriodIds(periodIds)) {
            if (isEmpty(content)) continue;

            byPeriod.computeIfAbsent(content.getPeriod().getId(), ignored -> new ArrayList<>())
                    .add(toDto(content));
        }
        return byPeriod;
    }

    /** One entry, as the case page reads it beside the day it was written. */
    public PeriodContentDto get(Long contentId)
    {
        return toDto(content(contentId));
    }

    public PeriodContentDto save(Long periodId, PeriodContentUpdateDto dto)
    {
        Period period = period(periodId);
        LocalDate date = parseDate(dto.getDate());
        requireInPeriod(period, date);

        Long contentId = periodContentDao.findByDate(periodId, Date.valueOf(date))
                .map(existing -> {
                    periodContentDao.updateContent(existing.getId(), dto.getContent());
                    return existing.getId();
                })
                .orElseGet(() -> {
                    PeriodContent created = new PeriodContent();
                    created.setPeriod(period);
                    created.setDate(Date.valueOf(date));
                    created.setContent(dto.getContent());
                    periodContentDao.create(created);
                    return created.getId();
                });

        // Answered from what was just written rather than read back: the entry is already in
        // this request's sights, and reading it again hands back the version from before.
        return new PeriodContentDto(contentId, date.toString(), dto.getContent(), images(contentId));
    }

    public PeriodContentDto.Image addImage(Long contentId, byte[] uploaded, String mime)
    {
        PeriodContent content = content(contentId);
        if (periodContentDao.countImages(contentId) >= MAX_IMAGES_PER_ENTRY) {
            throw new InvalidInputException("an entry can hold at most "
                    + MAX_IMAGES_PER_ENTRY + " images");
        }

        ImageService.Prepared prepared = imageService.prepare(uploaded, mime);
        PeriodContentImage image = new PeriodContentImage();
        image.setContent(content);
        image.setMime(prepared.mime());
        image.setWidth(prepared.width());
        image.setHeight(prepared.height());
        image.setImage(prepared.image());
        image.setThumbnail(prepared.thumbnail());
        image.setUploadedAt(LocalDateTime.now());

        periodContentDao.createImage(image);
        return new PeriodContentDto.Image(image.getId(), image.getWidth(), image.getHeight());
    }

    public void deleteImage(Long imageId)
    {
        image(imageId);
        periodContentDao.deleteImage(imageId);
    }

    /** The bytes of one image, the only place they are ever read. */
    public Picture readImage(Long imageId, boolean thumbnail)
    {
        PeriodContentImage image = image(imageId);
        return new Picture(image.getMime(), periodContentDao.readImageBytes(imageId, thumbnail));
    }

    public record Picture(String mime, byte[] bytes) {}

    /**
     * Keeps an entry inside the period it was written for. Where the window cannot be worked out
     * - nothing reported here, nothing reported before it - any date is taken: the page marks
     * such an entry rather than refusing it, and the user puts it right.
     */
    private void requireInPeriod(Period period, LocalDate date)
    {
        PeriodDateWindowService.Resolution resolution = periodDateWindowService.resolve(
                period, periodDao.list(period.getCompany().getId()));
        PeriodDateWindowService.DateWindow window = resolution.window();
        if (window == null || window.includes(date)) return;

        throw new InvalidInputException("content date '" + date + "' must be between '"
                + window.start() + "' and '" + window.end() + "' for period '"
                + period.getName() + "'");
    }

    private static boolean isEmpty(PeriodContent content)
    {
        return content.getImages().isEmpty() && Utils.isBlankContent(content.getContent());
    }

    private List<PeriodContentDto.Image> images(Long contentId)
    {
        return periodContentDao.listImageSizes(contentId).stream()
                .map(row -> new PeriodContentDto.Image(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).intValue(),
                        ((Number) row[2]).intValue()))
                .toList();
    }

    private static PeriodContentDto toDto(PeriodContent content)
    {
        return new PeriodContentDto(
                content.getId(),
                content.getDate().toString(),
                content.getContent(),
                content.getImages().stream()
                        .map(image -> new PeriodContentDto.Image(
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

    private Period period(Long periodId)
    {
        try {
            return periodDao.get(periodId);
        } catch (NoResultException exception) {
            throw new InvalidInputException("period with id '" + periodId + "' not found");
        }
    }

    private PeriodContent content(Long contentId)
    {
        try {
            return periodContentDao.get(contentId);
        } catch (NoResultException exception) {
            throw new InvalidInputException("content with id '" + contentId + "' not found");
        }
    }

    private PeriodContentImage image(Long imageId)
    {
        try {
            return periodContentDao.getImage(imageId);
        } catch (NoResultException exception) {
            throw new InvalidInputException("image with id '" + imageId + "' not found");
        }
    }
}
