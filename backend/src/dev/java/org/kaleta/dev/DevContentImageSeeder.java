package org.kaleta.dev;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.kaleta.persistence.api.CompanyDao;
import org.kaleta.persistence.api.PeriodContentDao;
import org.kaleta.persistence.api.PeriodDao;
import org.kaleta.persistence.entity.Period;
import org.kaleta.persistence.entity.PeriodContent;
import org.kaleta.persistence.api.CaseEntryDao;
import org.kaleta.persistence.entity.CaseEntry;
import org.kaleta.service.CaseEntryService;
import org.kaleta.service.PeriodContentService;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Gives the dev data a few pictures to look at, since research with charts is the point of the
 * feature and a seeded database cannot carry blobs without bloating the SQL.
 *
 * They are drawn here and put through the same upload the app uses, so what dev shows is what a
 * real upload produces - downscaled, with a thumbnail beside it.
 */
@ApplicationScoped
@IfBuildProperty(name = "dev.seed.images", stringValue = "true")
public class DevContentImageSeeder
{
    private static final String TICKER = "NVDA";
    private static final String PERIOD = "27Q1";

    @Inject
    CompanyDao companyDao;
    @Inject
    PeriodDao periodDao;
    @Inject
    PeriodContentDao periodContentDao;
    @Inject
    PeriodContentService periodContentService;
    @Inject
    CaseEntryDao caseEntryDao;
    @Inject
    CaseEntryService caseEntryService;

    void onStart(@Observes StartupEvent event)
    {
        Long companyId = companyDao.getByTicker(TICKER).getId();
        List<Long> periodIds = periodDao.list(companyId).stream()
                .filter(period -> PERIOD.equals(period.getName().toString()))
                .map(Period::getId)
                .toList();

        int drawn = 0;
        boolean first = true;
        for (PeriodContent content : periodContentDao.listByPeriodIds(periodIds)) {
            if (!content.getImages().isEmpty()) continue;

            // The first day carries more than its text is tall, which is the case the strip has
            // to handle by scrolling.
            int count = first ? 6 : 3;
            first = false;
            for (int index = 0; index < count; index++) {
                periodContentService.addImage(
                        content.getId(), chart(content.getDate().toString(), index), "image/png");
                drawn++;
            }
        }
        if (drawn > 0) {
            io.quarkus.logging.Log.info("Seeded " + drawn + " research images for "
                    + TICKER + " " + PERIOD);
        }

        seedCaseImages();
    }

    /** The same charts, into the days of the cases, so a case reads like a real one. */
    private void seedCaseImages()
    {
        int drawn = 0;
        for (long caseId : new long[]{1L, 2L}) {
            int index = 0;
            for (CaseEntry entry : caseEntryDao.listByCaseId(caseId)) {
                if (!entry.getImages().isEmpty()) continue;

                // Two days of the first case carry pictures: one a few, one more than fit
                // across a phone, which is the case the strip has to scroll.
                int count = index == 1 ? 3 : index == 2 ? 6 : 0;
                for (int picture = 0; picture < count; picture++) {
                    caseEntryService.addImage(
                            entry.getId(), chart(entry.getDate().toString(), picture), "image/png");
                    drawn++;
                }
                index++;
            }
        }
        if (drawn > 0) {
            io.quarkus.logging.Log.info("Seeded " + drawn + " case images");
        }
    }

    private static byte[] chart(String date, int index)
    {
        int width = 1200 + index * 160;
        int height = 700;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);

        graphics.setColor(new Color(0x90, 0x90, 0x90));
        graphics.setStroke(new BasicStroke(2));
        graphics.drawLine(90, height - 90, width - 60, height - 90);
        graphics.drawLine(90, 60, 90, height - 90);

        String[] titles = {"Data-centre revenue", "Gross margin", "Order book"};
        graphics.setColor(new Color(0x33, 0x33, 0x33));
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 34));
        graphics.drawString(titles[index % titles.length] + "  -  " + date, 90, 42);

        Color[] colours = {new Color(0x19, 0x76, 0xD2), new Color(0x2E, 0x7D, 0x32), new Color(0xED, 0x6C, 0x02)};
        graphics.setColor(colours[index % colours.length]);
        graphics.setStroke(new BasicStroke(5));
        int points = 10;
        int[] xs = new int[points];
        int[] ys = new int[points];
        for (int point = 0; point < points; point++) {
            xs[point] = 110 + point * (width - 200) / (points - 1);
            double growth = Math.pow(point, 1.4 + index * 0.25);
            ys[point] = (int) (height - 120 - growth * (height - 260) / Math.pow(points - 1, 1.4 + index * 0.25));
        }
        graphics.drawPolyline(xs, ys, points);
        graphics.dispose();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", bytes);
        } catch (IOException exception) {
            throw new IllegalStateException("the dev chart could not be drawn", exception);
        }
        return bytes.toByteArray();
    }
}
