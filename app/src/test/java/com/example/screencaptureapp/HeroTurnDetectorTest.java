package com.example.screencaptureapp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HeroTurnDetectorTest {
    private static final int WIDTH = 490;
    private static final int HEIGHT = 1000;

    @Test
    public void requiresRedFoldAndRightActionControl() {
        FakePixels pixels = new FakePixels(WIDTH, HEIGHT, 0xff145a55);
        pixels.fill(0.61, 0.70, 0.76, 0.78, 0xff00dc00);
        assertFalse(HeroTurnDetector.isHeroTurn(WIDTH, HEIGHT, pixels));

        pixels.fill(0.24, 0.70, 0.40, 0.78, 0xffdc0000);
        assertTrue(HeroTurnDetector.isHeroTurn(WIDTH, HEIGHT, pixels));
    }

    @Test
    public void acceptsBrightCyanControlOnlyWithWhiteIcon() {
        FakePixels pixels = new FakePixels(WIDTH, HEIGHT, 0xff145a55);
        pixels.fill(0.24, 0.70, 0.40, 0.78, 0xffdc0000);
        pixels.fill(0.61, 0.70, 0.76, 0.78, 0xff00dcb7);
        assertFalse(HeroTurnDetector.isHeroTurn(WIDTH, HEIGHT, pixels));

        pixels.fill(0.61, 0.70, 0.63, 0.78, 0xffffffff);
        assertTrue(HeroTurnDetector.isHeroTurn(WIDTH, HEIGHT, pixels));
    }

    @Test
    public void rejectsUnsupportedLandscapeCanvas() {
        FakePixels pixels = new FakePixels(1000, 490, 0xffff0000);
        assertFalse(HeroTurnDetector.isHeroTurn(1000, 490, pixels));
    }

    private static final class FakePixels implements HeroTurnDetector.PixelReader {
        private final int width;
        private final int height;
        private final int[] pixels;

        FakePixels(int width, int height, int initialColor) {
            this.width = width;
            this.height = height;
            this.pixels = new int[width * height];
            java.util.Arrays.fill(this.pixels, initialColor);
        }

        void fill(double x0, double y0, double x1, double y1, int color) {
            int left = (int) (width * x0);
            int right = (int) (width * x1);
            int top = (int) (height * y0);
            int bottom = (int) (height * y1);
            for (int y = top; y < bottom; y++) {
                for (int x = left; x < right; x++) {
                    pixels[y * width + x] = color;
                }
            }
        }

        @Override
        public int getPixel(int x, int y) {
            return pixels[y * width + x];
        }
    }
}
