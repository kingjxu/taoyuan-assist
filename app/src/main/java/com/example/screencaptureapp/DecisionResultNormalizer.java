package com.example.screencaptureapp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

final class DecisionResultNormalizer {
    private static final String FOLD_ACTION = "fold";
    private static final String FOLD_TEXT = "弃牌";
    private static final String CHECK_TEXT = "过牌";

    private DecisionResultNormalizer() {}

    static String applyFreeCheckFallback(String payload, String decisionAction, boolean canCheck) {
        if (payload == null || !canCheck || !FOLD_ACTION.equals(decisionAction)) {
            return payload;
        }
        try {
            JsonElement parsed = JsonParser.parseString(payload);
            if (!parsed.isJsonObject()) {
                return payload;
            }
            JsonObject response = parsed.getAsJsonObject();
            JsonElement result = response.get("Result");
            if (result == null || !result.isJsonPrimitive()
                    || !result.getAsJsonPrimitive().isString()
                    || !FOLD_TEXT.equals(result.getAsString())) {
                return payload;
            }
            response.addProperty("Result", CHECK_TEXT);
            return response.toString();
        } catch (RuntimeException ignored) {
            return payload;
        }
    }
}
