package com.gamesaves.gamesaves.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Pure JDK image thumbnail generation.
 * Cover images: center-crop to 16:9, then resize where the number = short edge (height).
 *   e.g. 360 → 640×360 (like "360p").
 * README images: proportional resize where 720 = short edge, no crop.
 */
public class ImageThumbnailService {

    private static final Logger log = LoggerFactory.getLogger(ImageThumbnailService.class);

    // Target short-edge sizes for cover thumbnails (height for 16:9 landscape)
    public static final int COVER_SMALL  = 270;  // 480×270
    public static final int COVER_MEDIUM = 360;  // 640×360
    public static final int COVER_LARGE  = 720;  // 1280×720

    // Cover target aspect ratio: 16 / 9
    private static final double TARGET_RATIO = 16.0 / 9.0;

    // README image max short edge
    public static final int README_MAX_SHORT = 720;

    // JPEG quality (0.0–1.0). 0.85 is the sweet spot: near-lossless visually, ~3× smaller than PNG.
    private static final float JPEG_QUALITY = 0.85f;

    /**
     * Generate cover thumbnails at 270p, 360p, 720p (short edge = height for 16:9).
     * All center-cropped to 16:9, output as JPEG.
     */
    public static void generateCoverThumbnails(Path originalPath, Path outputDir) throws IOException {
        BufferedImage original = ImageIO.read(originalPath.toFile());
        if (original == null) {
            // WebP and other formats not natively supported by JDK
            // Thumbnails are skipped — frontend falls back to original image
            log.warn("Cannot decode image for thumbnails (format not supported by JDK): {}."
                    + " Original will be used directly.", originalPath.getFileName());
            return;
        }

        int[] shortEdges = {COVER_SMALL, COVER_MEDIUM, COVER_LARGE};
        for (int se : shortEdges) {
            int h = se;                                     // short edge = height
            int w = (int) Math.round(h * TARGET_RATIO);     // width from 16:9 ratio
            BufferedImage thumb = cropToAspectRatio(original, TARGET_RATIO);
            thumb = resize(thumb, w, h);
            Path outPath = outputDir.resolve("cover_thumb_" + se + ".jpg");
            writeJpeg(thumb, outPath);
            log.debug("Cover thumbnail generated: {} ({}×{})", outPath.getFileName(), w, h);
        }
    }

    /**
     * Generate a proportional 720p (short edge) thumbnail for a README image.
     * If the original's short edge is already <= 720px, this is a no-op (returns false).
     */
    public static boolean generateReadmeThumbnail(Path originalPath) throws IOException {
        BufferedImage original = ImageIO.read(originalPath.toFile());
        if (original == null) {
            log.warn("Unable to read README image: {}", originalPath);
            return false;
        }

        int origW = original.getWidth();
        int origH = original.getHeight();
        int origShort = Math.min(origW, origH);
        if (origShort <= README_MAX_SHORT) {
            return false; // Already small enough
        }

        double scale = (double) README_MAX_SHORT / origShort;
        int newW = (int) Math.round(origW * scale);
        int newH = (int) Math.round(origH * scale);
        BufferedImage thumb = resize(original, newW, newH);

        String origName = originalPath.getFileName().toString();
        String thumbName = stripExtension(origName) + "_thumb.jpg";
        Path thumbPath = originalPath.resolveSibling(thumbName);
        writeJpeg(thumb, thumbPath);
        log.debug("README thumbnail generated: {} ({}×{})", thumbPath.getFileName(), newW, newH);
        return true;
    }

    /**
     * Get the total size of all image files under a directory tree, for storage tracking.
     */
    public static long totalImageSize(Path dir) throws IOException {
        if (!Files.exists(dir)) return 0;
        long[] total = {0};
        Files.walk(dir).filter(Files::isRegularFile).forEach(p -> {
            String name = p.getFileName().toString().toLowerCase();
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".gif") || name.endsWith(".webp")) {
                try {
                    total[0] += Files.size(p);
                } catch (IOException ignored) {}
            }
        });
        return total[0];
    }

    // ---- internal helpers ----

    /**
     * Center-crop to the target aspect ratio.
     */
    private static BufferedImage cropToAspectRatio(BufferedImage src, double ratio) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        double srcRatio = (double) srcW / srcH;

        int cropW, cropH, x, y;
        if (Math.abs(srcRatio - ratio) < 0.01) {
            // Already matches, no cropping needed
            return src;
        } else if (srcRatio > ratio) {
            // Source is wider than target — crop left and right
            cropH = srcH;
            cropW = (int) Math.round(cropH * ratio);
            x = (srcW - cropW) / 2;
            y = 0;
        } else {
            // Source is taller than target — crop top and bottom
            cropW = srcW;
            cropH = (int) Math.round(cropW / ratio);
            x = 0;
            y = (srcH - cropH) / 2;
        }

        return src.getSubimage(x, y, cropW, cropH);
    }

    /**
     * Resize using bilinear interpolation for quality.
     */
    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // White background for transparency support
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
            g.drawImage(src, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return result;
    }

    private static void writeJpeg(BufferedImage image, Path path) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("JPEG").next();
        try (OutputStream out = Files.newOutputStream(path);
             ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(JPEG_QUALITY);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
