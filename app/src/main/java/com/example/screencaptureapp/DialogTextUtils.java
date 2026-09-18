package com.example.screencaptureapp;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

final class DialogTextUtils {
    private DialogTextUtils() {
    }

    static SpannableString redBold(String text) {
        SpannableString styledText = new SpannableString(text == null ? "" : text);
        styledText.setSpan(new ForegroundColorSpan(Color.RED), 0, styledText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        styledText.setSpan(new StyleSpan(Typeface.BOLD), 0, styledText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return styledText;
    }
}
