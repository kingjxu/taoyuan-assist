package com.example.screencaptureapp;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import okio.Buffer;
import okhttp3.RequestBody;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StrategyProfileSettingsTest {
    private Context context;

    @Before public void setup() {
        context = RuntimeEnvironment.getApplication();
        StrategyProfileSettings.setSelectedIndex(context, 0);
    }

    @Test public void exposesOnlySupportedProfilesAndDefaultsToZeroValues() {
        String[] expectedLabels = {
                "默认", "VPIP42_PFR20", "VPIP33_PFR15", "VPIP54_PFR11",
                "VPIP38_PFR14", "VPIP40_PFR10", "VPIP55_PFR25"
        };
        int[][] expectedValues = {
                {0, 0}, {42, 20}, {33, 15}, {54, 11}, {38, 14}, {40, 10}, {55, 25}
        };

        assertEquals(expectedLabels.length, StrategyProfileSettings.PROFILES.length);
        for (int index = 0; index < expectedLabels.length; index++) {
            StrategyProfileSettings.setSelectedIndex(context, index);
            StrategyProfileSettings.Profile profile =
                    StrategyProfileSettings.getSelectedProfile(context);
            assertEquals(expectedLabels[index], profile.name);
            assertEquals(expectedValues[index][0], profile.vpip);
            assertEquals(expectedValues[index][1], profile.pfr);
        }

        StrategyProfileSettings.setSelectedIndex(context, 99);
        StrategyProfileSettings.Profile fallback =
                StrategyProfileSettings.getSelectedProfile(context);
        assertEquals("默认", fallback.name);
        assertEquals(0, fallback.vpip);
        assertEquals(0, fallback.pfr);
    }

    @Test public void uploadBodyIncludesVpIpAndPfrAsNumbers() throws Exception {
        NetworkManager manager = new NetworkManager(context, "account-id", "token");
        int[][] profiles = {{0, 0}, {42, 20}};
        for (int[] profile : profiles) {
            LatencyTraceStore.TraceRecord trace =
                    new LatencyTraceStore.TraceRecord("trace-id", 1L);
            RequestBody body = manager.createUploadRequestBody(
                    new byte[]{1, 2}, 2, 123L, trace, profile[0], profile[1]);
            Buffer buffer = new Buffer();
            body.writeTo(buffer);

            JsonObject json = JsonParser.parseString(buffer.readUtf8()).getAsJsonObject();
            assertEquals("AQI=", json.getAsJsonArray("images").get(0).getAsString());
            assertEquals(123L, json.get("timestamp").getAsLong());
            assertEquals("account-id", json.get("uuid").getAsString());
            assertEquals(profile[0], json.get("vpip").getAsInt());
            assertEquals(profile[1], json.get("pfr").getAsInt());
        }
    }
}
