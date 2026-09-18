package com.example.screencaptureapp;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Keeps observable overlay failures until the user can see them inside the app. */
final class OverlayDiagnostics {
    static final String ACTION_CHANGED = "com.example.screencaptureapp.OVERLAY_STATUS_CHANGED";
    private static final String PREFS = "overlay_diagnostics";
    private static final String ERROR = "error";

    static void report(Context context, String message, Exception error) {
        Log.e("OverlayDiagnostics", message, error);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(ERROR, message).apply();
        context.sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));
    }

    static String pendingError(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ERROR, "");
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(ERROR).apply();
    }
}
