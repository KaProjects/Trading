package org.kaleta.service;

import jakarta.enterprise.context.ApplicationScoped;
import org.kaleta.rest.error.InvalidInputException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

/**
 * Prepares an uploaded picture for storage.
 *
 * These are charts and screenshots rather than photographs, so the work is aimed at keeping thin
 * lines and small type readable: the long edge is brought down to a size the page can actually
 * show, a PNG stays a PNG rather than being smeared by JPEG compression, and a thumbnail is kept
 * beside the picture so a period full of images does not have to load them all.
 *
 * The decode is the dangerous part on a small heap - a 4000px screenshot is 48MB once unpacked -
 * so the size is read from the header first, anything absurd is refused, and the reader is asked
 * to subsample while decoding rather than afterwards.
 */
@ApplicationScoped
public class ImageService
{
    public static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    static final int MAX_PIXELS = 40_000_000;
    static final int MAX_EDGE = 1600;
    static final int THUMBNAIL_EDGE = 320;
    static final float JPEG_QUALITY = 0.85f;

    public record Prepared(String mime, int width, int height, byte[] image, byte[] thumbnail) {}

    public Prepared prepare(byte[] uploaded, String declaredMime)
    {
        if (uploaded == null || uploaded.length == 0) {
            throw new InvalidInputException("the uploaded file is empty");
        }
        if (uploaded.length > MAX_UPLOAD_BYTES) {
            throw new InvalidInputException("the image is larger than "
                    + (MAX_UPLOAD_BYTES / (1024 * 1024)) + "MB");
        }

        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(uploaded))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw new InvalidInputException("this is not a PNG or a JPEG image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean png = format.contains("png");
                if (!png && !format.contains("jpeg") && !format.contains("jpg")) {
                    throw new InvalidInputException("only PNG and JPEG images can be added, not "
                            + (declaredMime == null || declaredMime.isBlank() ? format : declaredMime));
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if ((long) width * height > MAX_PIXELS) {
                    throw new InvalidInputException("the image is too large to process: "
                            + width + "x" + height + " pixels");
                }

                BufferedImage decoded = decode(reader, width, height);
                BufferedImage full = resized(decoded, MAX_EDGE, png);
                BufferedImage small = resized(decoded, THUMBNAIL_EDGE, png);
                return new Prepared(
                        png ? "image/png" : "image/jpeg",
                        full.getWidth(),
                        full.getHeight(),
                        encode(full, png),
                        encode(small, png));
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new InvalidInputException("the image could not be read: " + exception.getMessage());
        }
    }

    /** Decodes at the smallest whole fraction that still covers the size we need. */
    private BufferedImage decode(ImageReader reader, int width, int height) throws IOException
    {
        int step = 1;
        while (Math.max(width, height) / (step + 1) >= MAX_EDGE) {
            step++;
        }
        if (step == 1) return reader.read(0);

        ImageReadParam parameters = reader.getDefaultReadParam();
        parameters.setSourceSubsampling(step, step, 0, 0);
        return reader.read(0, parameters);
    }

    private BufferedImage resized(BufferedImage source, int edge, boolean keepAlpha)
    {
        int longest = Math.max(source.getWidth(), source.getHeight());
        int width = source.getWidth();
        int height = source.getHeight();
        if (longest > edge) {
            double scale = (double) edge / longest;
            width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        } else if (source.getType() == (keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB)) {
            return source;
        }

        BufferedImage target = new BufferedImage(width, height,
                keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }

    private byte[] encode(BufferedImage image, boolean png) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (png) {
            ImageIO.write(image, "png", bytes);
        } else {
            javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            try (javax.imageio.stream.ImageOutputStream output =
                         ImageIO.createImageOutputStream(bytes)) {
                javax.imageio.ImageWriteParam parameters = writer.getDefaultWriteParam();
                parameters.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(JPEG_QUALITY);
                writer.setOutput(output);
                writer.write(null, new javax.imageio.IIOImage(image, null, null), parameters);
            } finally {
                writer.dispose();
            }
        }
        return bytes.toByteArray();
    }
}
