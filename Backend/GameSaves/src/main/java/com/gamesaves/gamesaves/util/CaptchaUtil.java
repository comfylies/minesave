package com.gamesaves.gamesaves.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;

/**
 * 图形验证码生成工具 — 纯自研，仅依赖 JDK {@link java.awt} / {@link javax.imageio}。
 * 无第三方验证码库（非 Kaptcha / EasyCaptcha / Patchca）。
 */

/**
 * 图形验证码生成工具
 * 生成包含扭曲文字+干扰线+噪点的图片验证码，返回base64图片和验证码文本
 * 用于登录接口防机器人/暴力破解
 */
public class CaptchaUtil {

    private static final int WIDTH = 130;
    private static final int HEIGHT = 48;
    private static final int CODE_LENGTH = 4;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 排除易混淆字符 0/O/1/I
    private static final Random RANDOM = new SecureRandom();

    private CaptchaUtil() {}

    /**
     * 验证码生成结果
     */
    public record CaptchaResult(String base64Image, String code) {}

    /**
     * 生成图形验证码
     * @return CaptchaResult 包含base64图片和验证码文本
     */
    public static CaptchaResult generate() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // 抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 背景（浅灰渐变）
        GradientPaint gradient = new GradientPaint(0, 0, new Color(245, 247, 250),
                WIDTH, HEIGHT, new Color(230, 235, 240));
        g.setPaint(gradient);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 生成随机验证码
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        String codeStr = code.toString();

        // 绘制文字（带旋转和偏移）
        Font[] fonts = {
                new Font("Arial", Font.BOLD, 30),
                new Font("Georgia", Font.BOLD, 28),
                new Font("Verdana", Font.BOLD, 29)
        };
        int charWidth = WIDTH / (CODE_LENGTH + 1);
        for (int i = 0; i < CODE_LENGTH; i++) {
            g.setFont(fonts[RANDOM.nextInt(fonts.length)]);
            // 随机颜色（深色系）
            g.setColor(new Color(
                    30 + RANDOM.nextInt(60),
                    30 + RANDOM.nextInt(60),
                    30 + RANDOM.nextInt(60)));

            // 旋转角度 -25° ~ 25°
            double angle = Math.toRadians(-25 + RANDOM.nextInt(50));
            int x = charWidth * (i + 1) - 8 + RANDOM.nextInt(10);
            int y = 32 + RANDOM.nextInt(8);

            g.rotate(angle, x, y);
            g.drawString(String.valueOf(codeStr.charAt(i)), x, y);
            g.rotate(-angle, x, y); // 恢复旋转
        }

        // 干扰线（2-3条）
        g.setStroke(new BasicStroke(1.5f));
        for (int i = 0; i < 2 + RANDOM.nextInt(2); i++) {
            g.setColor(new Color(
                    160 + RANDOM.nextInt(80),
                    160 + RANDOM.nextInt(80),
                    170 + RANDOM.nextInt(70)));
            int x1 = RANDOM.nextInt(WIDTH / 4);
            int y1 = RANDOM.nextInt(HEIGHT);
            int x2 = WIDTH - RANDOM.nextInt(WIDTH / 4);
            int y2 = RANDOM.nextInt(HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }

        // 噪点（约30个）
        for (int i = 0; i < 30; i++) {
            g.setColor(new Color(
                    180 + RANDOM.nextInt(70),
                    180 + RANDOM.nextInt(70),
                    180 + RANDOM.nextInt(70)));
            int x = RANDOM.nextInt(WIDTH);
            int y = RANDOM.nextInt(HEIGHT);
            g.fillOval(x, y, 2, 2);
        }

        g.dispose();

        // 转base64
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            String base64 = "data:image/png;base64," +
                    Base64.getEncoder().encodeToString(baos.toByteArray());
            return new CaptchaResult(base64, codeStr);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate captcha image", e);
        }
    }
}
