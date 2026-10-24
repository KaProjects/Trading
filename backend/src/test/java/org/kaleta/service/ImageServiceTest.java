package org.kaleta.service;

import org.junit.jupiter.api.Test;
import org.kaleta.rest.error.InvalidInputException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageServiceTest
{
    private final ImageService imageService = new ImageService();

    @Test
    void bringsALargeScreenshotDownToTheSizeThePageShows() throws IOException
    {
        ImageService.Prepared prepared = imageService.prepare(png(3200, 1800), "image/png");

        assertThat(prepared.width(), is(1600));
        assertThat(prepared.height(), is(900));
        assertThat(sizeOf(prepared.image()), is(new int[]{1600, 900}));
        // The thumbnail keeps the proportions and is what a strip of pictures loads.
        assertThat(sizeOf(prepared.thumbnail()), is(new int[]{320, 180}));
        assertThat(prepared.thumbnail().length, is(lessThan(prepared.image().length)));
    }

    @Test
    void leavesASmallImageAtItsOwnSize() throws IOException
    {
        ImageService.Prepared prepared = imageService.prepare(png(600, 400), "image/png");

        assertThat(prepared.width(), is(600));
        assertThat(prepared.height(), is(400));
        assertThat(sizeOf(prepared.image()), is(new int[]{600, 400}));
        assertThat(sizeOf(prepared.thumbnail()), is(new int[]{320, 213}));
    }

    @Test
    void keepsAPngAPng() throws IOException
    {
        ImageService.Prepared prepared = imageService.prepare(png(2000, 1000), "image/png");

        assertThat(prepared.mime(), is("image/png"));
        // A chart is mostly flat colour, so the re-encode is far smaller than the raw pixels.
        assertThat(prepared.image().length, is(lessThan(1600 * 800 * 4)));
    }

    @Test
    void keepsAJpegAJpeg() throws IOException
    {
        ImageService.Prepared prepared = imageService.prepare(jpeg(2400, 1200), "image/jpeg");

        assertThat(prepared.mime(), is("image/jpeg"));
        assertThat(prepared.width(), is(1600));
        assertThat(prepared.height(), is(800));
    }

    @Test
    void refusesWhatItCannotUse() throws IOException
    {
        assertThat(assertThrows(InvalidInputException.class,
                () -> imageService.prepare(new byte[0], "image/png")).getMessage(),
                containsString("empty"));

        assertThat(assertThrows(InvalidInputException.class,
                () -> imageService.prepare("not an image at all".getBytes(), "text/plain"))
                        .getMessage(),
                containsString("not a PNG or a JPEG"));

        byte[] oversized = new byte[ImageService.MAX_UPLOAD_BYTES + 1];
        assertThat(assertThrows(InvalidInputException.class,
                () -> imageService.prepare(oversized, "image/png")).getMessage(),
                containsString("larger than 10MB"));
    }

    private static int[] sizeOf(byte[] encoded) throws IOException
    {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(encoded));
        return new int[]{image.getWidth(), image.getHeight()};
    }

    /** Something chart-like: flat background, a few lines, a block of colour. */
    private static byte[] png(int width, int height) throws IOException
    {
        return encode(drawn(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    private static byte[] jpeg(int width, int height) throws IOException
    {
        return encode(drawn(width, height, BufferedImage.TYPE_INT_RGB), "jpeg");
    }

    private static BufferedImage drawn(int width, int height, int type)
    {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLUE);
        for (int x = 0; x < width; x += 40) {
            graphics.drawLine(x, 0, x, height);
        }
        graphics.setColor(Color.RED);
        graphics.fillRect(width / 4, height / 4, width / 2, height / 2);
        graphics.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return bytes.toByteArray();
    }
}
