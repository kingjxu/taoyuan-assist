package com.example.screencaptureapp;

import android.content.Context;
import android.content.SharedPreferences;

final class OverlayTextSettings {
    static final int DEFAULT_FONT_SIZE_SP = 20;
    static final int MIN_FONT_SIZE_SP = 14;
    static final int MAX_FONT_SIZE_SP = 36;

    private static final String PREFS_NAME = "overlay_display_settings";
    private static final String KEY_FONT_SIZE_SP = "font_size_sp";

    private OverlayTextSettings() {
    }

    static int getFontSizeSp(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return clampFontSize(preferences.getInt(KEY_FONT_SIZE_SP, DEFAULT_FONT_SIZE_SP));
    }

    static void setFontSizeSp(Context context, int fontSizeSp) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_FONT_SIZE_SP, clampFontSize(fontSizeSp))
                .apply();
    }

    static int clampFontSize(int fontSizeSp) {
        return Math.max(MIN_FONT_SIZE_SP, Math.min(MAX_FONT_SIZE_SP, fontSizeSp));
    }
}
