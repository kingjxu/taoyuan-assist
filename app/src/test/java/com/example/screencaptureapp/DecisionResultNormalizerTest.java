package com.example.screencaptureapp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DecisionResultNormalizerTest {
    @Test public void convertsFoldToCheckOnlyWhenServerConfirmsFreeCheck() {
        String input = "{\"Result\":\"弃牌\",\"HeroCard\":\"[\\\"A-D\\\",\\\"J-S\\\"]\"}";
        String output = DecisionResultNormalizer.applyFreeCheckFallback(input, "fold", true);

        JsonObject parsed = JsonParser.parseString(output).getAsJsonObject();
        assertEquals("过牌", parsed.get("Result").getAsString());
        assertEquals("[\"A-D\",\"J-S\"]", parsed.get("HeroCard").getAsString());
    }

    @Test public void keepsFoldWhenHeroFacesAWager() {
        String input = "{\"Result\":\"弃牌\"}";
        assertEquals(input, DecisionResultNormalizer.applyFreeCheckFallback(input, "fold", false));
    }

    @Test public void keepsPayloadWhenActionOrResultDoesNotMatch() {
        String check = "{\"Result\":\"过牌\"}";
        String fold = "{\"Result\":\"弃牌\"}";
        String paddedFold = "{\"Result\":\" 弃牌 \"}";
        assertEquals(check, DecisionResultNormalizer.applyFreeCheckFallback(check, "check", true));
        assertEquals(check, DecisionResultNormalizer.applyFreeCheckFallback(check, "fold", true));
        assertEquals(fold, DecisionResultNormalizer.applyFreeCheckFallback(fold, null, true));
        assertEquals(paddedFold,
                DecisionResultNormalizer.applyFreeCheckFallback(paddedFold, "fold", true));
    }

    @Test public void keepsMalformedOrUnexpectedPayload() {
        for (String payload : new String[]{"not-json", "[]", "{}", "{\"Result\":1}"}) {
            assertEquals(payload,
                    DecisionResultNormalizer.applyFreeCheckFallback(payload, "fold", true));
        }
    }
}
