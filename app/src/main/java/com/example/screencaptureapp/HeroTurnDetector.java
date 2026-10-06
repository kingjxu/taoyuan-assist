package com.example.screencaptureapp;

import android.graphics.Bitmap;

/** Lightweight AA Poker V2 action-button detector used only for latency tracing. */
final class HeroTurnDetector {
    private static final int SAMPLE_STEP = 3;

    private HeroTurnDetector() {
    }

    static boolean isHeroTurn(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return false;
        }
        return isHeroTurn(bitmap.getWidth(), bitmap.getHeight(), bitmap::getPixel);
    }

    static boolean isHeroTurn(int width, int height, PixelReader pixels) {
        if (pixels == null || width <= 0 || height <= width) {
            return false;
        }
        double aspectRatio = (double) width / (double) height;
        if (aspectRatio < 0.43d || aspectRatio > 0.52d) {
            return false;
        }

        double leftRed = ratio(width, height, pixels, 0.24d, 0.70d, 0.40d, 0.78d,
                HeroTurnDetector::isDeepRed);
        if (leftRed <= 0.06d) {
            return false;
        }

        double rightNonTableColor = ratio(width, height, pixels, 0.61d, 0.70d, 0.76d, 0.78d,
                HeroTurnDetector::isBrightNonTableColor);
        if (rightNonTableColor > 0.06d) {
            return true;
        }

        double rightCyan = ratio(width, height, pixels, 0.61d, 0.70d, 0.76d, 0.78d,
                HeroTurnDetector::isBrightCyan);
        double rightWhite = ratio(width, height, pixels, 0.61d, 0.70d, 0.76d, 0.78d,
                HeroTurnDetector::isWhite);
        return rightCyan > 0.06d && rightWhite > 0.05d;
    }

    private static double ratio(
            int width,
            int height,
            PixelReader pixels,
            double x0,
            double y0,
            double x1,
            double y1,
            PixelMatcher matcher
    ) {
        int left = Math.max(0, (int) Math.floor(width * x0));
        int top = Math.max(0, (int) Math.floor(height * y0));
        int right = Math.min(width, (int) Math.ceil(width * x1));
        int bottom = Math.min(height, (int) Math.ceil(height * y1));
        int matched = 0;
        int sampled = 0;
        for (int y = top; y < bottom; y += SAMPLE_STEP) {
            for (int x = left; x < right; x += SAMPLE_STEP) {
                sampled++;
                if (matcher.matches(pixels.getPixel(x, y))) {
                    matched++;
                }
            }
        }
        return sampled == 0 ? 0.0d : (double) matched / (double) sampled;
    }

    private static boolean isDeepRed(int color) {
        Hsv hsv = toHsv(color);
        return (hsv.hue <= 16 || hsv.hue >= 344) && hsv.saturation > 140 && hsv.value > 100;
    }

    private static boolean isBrightNonTableColor(int color) {
        Hsv hsv = toHsv(color);
        return hsv.saturation > 140 && hsv.value > 130 && (hsv.hue < 150 || hsv.hue > 190);
    }

    private static boolean isBrightCyan(int color) {
        Hsv hsv = toHsv(color);
        return hsv.saturation > 140 && hsv.value > 130 && hsv.hue >= 150 && hsv.hue <= 190;
    }

    private static boolean isWhite(int color) {
        Hsv hsv = toHsv(color);
        return hsv.saturation < 70 && hsv.value > 180;
    }

    private static Hsv toHsv(int color) {
        int red = (color >> 16) & 0xff;
        int green = (color >> 8) & 0xff;
        int blue = color & 0xff;
        int max = Math.max(red, Math.max(green, blue));
        int min = Math.min(red, Math.min(green, blue));
        int delta = max - min;
        int saturation = max == 0 ? 0 : delta * 255 / max;
        int hue;
        if (delta == 0) {
            hue = 0;
        } else if (max == red) {
            hue = 60 * (green - blue) / delta;
        } else if (max == green) {
            hue = 120 + 60 * (blue - red) / delta;
        } else {
            hue = 240 + 60 * (red - green) / delta;
        }
        if (hue < 0) {
            hue += 360;
        }
        return new Hsv(hue, saturation, max);
    }

    interface PixelReader {
        int getPixel(int x, int y);
    }

    private interface PixelMatcher {
        boolean matches(int color);
    }

    private static final class Hsv {
        final int hue;
        final int saturation;
        final int value;

        Hsv(int hue, int saturation, int value) {
            this.hue = hue;
            this.saturation = saturation;
            this.value = value;
        }
    }
}
