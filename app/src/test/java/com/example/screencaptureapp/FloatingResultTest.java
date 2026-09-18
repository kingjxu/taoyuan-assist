package com.example.screencaptureapp;

import org.junit.Test;
import static org.junit.Assert.*;

public class FloatingResultTest {
    @Test public void displaysOnlyResultText() {
        assertEquals("跟注 24", FloatingWindowService.resolveDisplayText(
                "__RESULT__:{\"Result\":\" 跟注 24 \",\"message\":\"不要显示\"}"));
    }

    @Test public void ignoresStatusMessagesAndUnwrappedJson() {
        for (String input : new String[]{null, "", "上传失败: timeout", "录制已停止，等待开始...",
                "{\"Result\":\"跟注\"}"}) {
            assertNull(input, FloatingWindowService.resolveDisplayText(input));
        }
    }

    @Test public void ignoresMissingEmptyOrWrongTypeResults() {
        for (String json : new String[]{"{}", "{\"result\":\"跟注\"}",
                "{\"Result\":null}", "{\"Result\":\"\"}", "{\"Result\":\" \\n\\t\"}",
                "{\"Result\":1}", "{\"Result\":true}", "{\"Result\":[]}",
                "{\"Result\":{}}", "null", "[]", "not-json", "{"}) {
            assertNull(json, FloatingWindowService.resolveDisplayText("__RESULT__:" + json));
        }
    }
}
