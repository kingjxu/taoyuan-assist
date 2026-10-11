package com.example.screencaptureapp;

import android.content.Context;
import android.content.SharedPreferences;

final class StrategyProfileSettings {
    private static final String PREFS_NAME = "decision_strategy_settings";
    private static final String KEY_PROFILE_NAME = "profile_name";

    static final Profile[] PROFILES = {
            new Profile("默认", 0, 0),
            new Profile("VPIP42_PFR20", 42, 20),
            new Profile("VPIP33_PFR15", 33, 15),
            new Profile("VPIP54_PFR11", 54, 11),
            new Profile("VPIP38_PFR14", 38, 14),
            new Profile("VPIP40_PFR10", 40, 10),
            new Profile("VPIP55_PFR25", 55, 25)
    };

    private StrategyProfileSettings() {
    }

    static String[] getLabels() {
        String[] labels = new String[PROFILES.length];
        for (int index = 0; index < PROFILES.length; index++) {
            labels[index] = PROFILES[index].name;
        }
        return labels;
    }

    static int getSelectedIndex(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String selectedName = preferences.getString(KEY_PROFILE_NAME, PROFILES[0].name);
        for (int index = 0; index < PROFILES.length; index++) {
            if (PROFILES[index].name.equals(selectedName)) {
                return index;
            }
        }
        return 0;
    }

    static Profile getSelectedProfile(Context context) {
        return PROFILES[getSelectedIndex(context)];
    }

    static void setSelectedIndex(Context context, int selectedIndex) {
        int safeIndex = selectedIndex >= 0 && selectedIndex < PROFILES.length
                ? selectedIndex : 0;
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_PROFILE_NAME, PROFILES[safeIndex].name)
                .apply();
    }

    static final class Profile {
        final String name;
        final int vpip;
        final int pfr;

        Profile(String name, int vpip, int pfr) {
            this.name = name;
            this.vpip = vpip;
            this.pfr = pfr;
        }
    }
}
