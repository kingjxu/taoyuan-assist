package com.example.screencaptureapp;

import android.content.Intent;
import android.os.Looper;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ServiceController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSettings;
import org.robolectric.util.ReflectionHelpers;
import java.lang.reflect.Proxy;
import java.time.Duration;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class OverlayWindowTest {
    private ServiceController<FloatingWindowService> serviceController;
    private ActivityController<MainActivity> activityController;

    @Before public void setup() {
        OverlayDiagnostics.clear(RuntimeEnvironment.getApplication());
        ShadowSettings.setCanDrawOverlays(true);
        // Robolectric does not automatically grant the app's own signature permission.
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
                "com.example.screencaptureapp.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
                android.Manifest.permission.INTERNET, android.Manifest.permission.ACCESS_NETWORK_STATE,
                android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        ReflectionHelpers.setStaticField(UserSessionManager.class, "instance", null);
    }

    @After public void cleanup() {
        if (serviceController != null) serviceController.destroy();
        if (activityController != null) activityController.destroy();
    }

    private FloatingWindowService service() {
        serviceController = Robolectric.buildService(FloatingWindowService.class).create();
        return serviceController.get();
    }

    @Test public void missingPermissionRecordsErrorWithoutKeepingFakeWindow() {
        ShadowSettings.setCanDrawOverlays(false);
        FloatingWindowService service = service();
        assertEquals(android.app.Service.START_NOT_STICKY,
                service.onStartCommand(new Intent(), 0, 1));
        assertNull(ReflectionHelpers.getField(service, "floatingView"));
        assertTrue(OverlayDiagnostics.pendingError(service).contains("权限未开启"));
    }

    @Test public void failedAddViewCanRetryAndSuccessClearsError() {
        FloatingWindowService service = service();
        WindowManager realManager = ReflectionHelpers.getField(service, "windowManager");
        WindowManager failing = (WindowManager) Proxy.newProxyInstance(
                WindowManager.class.getClassLoader(), new Class<?>[]{WindowManager.class},
                (proxy, method, args) -> { throw new WindowManager.BadTokenException("test rejection"); });
        ReflectionHelpers.setField(service, "windowManager", failing);
        service.onStartCommand(new Intent(), 0, 1);
        assertNull(ReflectionHelpers.getField(service, "floatingView"));
        assertTrue(OverlayDiagnostics.pendingError(service).contains("创建失败"));
        ReflectionHelpers.setField(service, "windowManager", realManager);
        assertEquals(android.app.Service.START_STICKY, service.onStartCommand(new Intent(), 0, 2));
        TextView text = ReflectionHelpers.getField(service, "feedbackText");
        assertEquals("等待反馈", text.getText().toString());
        assertEquals("", OverlayDiagnostics.pendingError(service));
    }

    @Test public void waitingHasNoTimeoutAndOnlyValidResultStartsFiveSeconds() {
        FloatingWindowService service = service();
        service.onStartCommand(new Intent(), 0, 1);
        TextView text = ReflectionHelpers.getField(service, "feedbackText");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        assertEquals("等待反馈", text.getText().toString());
        service.updateFeedbackText("上传失败");
        assertEquals("等待反馈", text.getText().toString());
        service.updateFeedbackText("__RESULT__:{\"Result\":\"跟注\"}");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
        assertEquals("跟注", text.getText().toString());
        service.updateFeedbackText("__RESULT__:{\"Result\":null}");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));
        assertEquals("", text.getText().toString());
        service.updateFeedbackText("__RESULT__:{\"Result\":\"弃牌\"}");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4));
        service.updateFeedbackText("__RESULT__:{\"Result\":\"跟注\"}");
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
        assertEquals("跟注", text.getText().toString());
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3));
        assertEquals("", text.getText().toString());
    }

    private MainActivity mainScreen() {
        UserSessionManager.getInstance(RuntimeEnvironment.getApplication())
                .saveLoginInfo("test-token", "test@example.invalid", "test");
        // Create only: onResume would intentionally issue a real quota HTTP request.
        activityController = Robolectric.buildActivity(MainActivity.class).create();
        MainActivity activity = activityController.get();
        ReflectionHelpers.setField(activity, "activityResumed", true);
        return activity;
    }

    @Test public void permissionPromptStaysInsideAppUntilSettingsChosen() {
        MainActivity activity = mainScreen();
        ShadowSettings.setCanDrawOverlays(false);
        ReflectionHelpers.callInstanceMethod(activity, "showOverlayProblemIfNeeded");
        AlertDialog dialog = ReflectionHelpers.getField(activity, "overlayDialog");
        assertTrue(dialog.isShowing());
        assertNull(shadowOf(activity).getNextStartedActivity());
        ReflectionHelpers.callInstanceMethod(activity, "showOverlayProblemIfNeeded");
        assertSame(dialog, ReflectionHelpers.getField(activity, "overlayDialog"));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        Intent settings = shadowOf(activity).getNextStartedActivity();
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, settings.getAction());
    }

    @Test public void foregroundFailureImmediatelyShowsInAppDialog() {
        MainActivity activity = mainScreen();
        OverlayDiagnostics.report(activity, "测试窗口创建失败", null);
        shadowOf(Looper.getMainLooper()).idle();
        AlertDialog dialog = ReflectionHelpers.getField(activity, "overlayDialog");
        assertTrue(dialog.isShowing());
    }

    @Test public void backgroundFailureIsShownWhenAppReturnsAndAcknowledged() {
        MainActivity activity = mainScreen();
        ReflectionHelpers.setField(activity, "activityResumed", false);
        OverlayDiagnostics.report(activity, "测试窗口创建失败", null);
        shadowOf(Looper.getMainLooper()).idle();
        assertNull(ReflectionHelpers.getField(activity, "overlayDialog"));
        assertEquals("测试窗口创建失败", OverlayDiagnostics.pendingError(activity));
        ReflectionHelpers.setField(activity, "activityResumed", true);
        ReflectionHelpers.callInstanceMethod(activity, "showOverlayProblemIfNeeded");
        AlertDialog dialog = ReflectionHelpers.getField(activity, "overlayDialog");
        assertTrue(dialog.isShowing());
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("", OverlayDiagnostics.pendingError(activity));
    }
}
