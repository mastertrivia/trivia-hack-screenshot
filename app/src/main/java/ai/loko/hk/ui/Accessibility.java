/*
 *   Copyright (C) 2018 SHUBHAM TYAGI
 *
 *    This file is part of Trivia Hack.
 *     Licensed under the GNU GENERAL PUBLIC LICENSE, Version 3.0 (the "License"); you may not
 *     use this file except in compliance with the License. You may obtain a copy of
 *     the License at
 *
 *     https://www.gnu.org/licenses/gpl-3.0
 *
 *    Trivia Hack is free software: you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation, either version 3 of the License, or
 *    (at your option) any later version.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Trivia Hack.  If not, see <http://www.gnu.org/licenses/>.
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package ai.loko.hk.ui;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import ai.loko.hk.ui.ocr.AccessibilityScreenshot;
import ai.loko.hk.ui.ocr.CapturePath;
import ai.loko.hk.ui.ocr.Screenshotter;

/**
 * Declared so Android can grant the accessibility permission from the home-screen
 * indicator. On Android 11+ it also captures the screen via
 * {@code takeScreenshot} so the floating button does not need a new
 * MediaProjection session (and therefore does not re-prompt "Start recording
 * or casting?") on every tap.
 */
public class Accessibility extends AccessibilityService {

    private static final String COMPONENT = "ai.loko.hk.ui/ai.loko.hk.ui.Accessibility";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile Accessibility sInstance;

    public static boolean isConnected() {
        return sInstance != null;
    }

    public static boolean isEnabled(Context context) {
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException ignored) {
            return false;
        }
        if (accessibilityEnabled != 1) {
            return false;
        }
        String settingValue = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (settingValue == null) {
            return false;
        }
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(settingValue);
        while (splitter.hasNext()) {
            if (splitter.next().equalsIgnoreCase(COMPONENT)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
    }

    @Override
    public void onDestroy() {
        if (sInstance == this) {
            sInstance = null;
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    public static void captureScreen(Screenshotter.ScreenshotCallback cb) {
        captureScreen(cb, 0);
    }

    private static void captureScreen(final Screenshotter.ScreenshotCallback cb, final int attempt) {
        Accessibility service = sInstance;
        if (service == null) {
            if (attempt < 8) {
                MAIN.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        captureScreen(cb, attempt + 1);
                    }
                }, 150);
                return;
            }
            cb.onScreenshot(null);
            return;
        }
        if (Build.VERSION.SDK_INT < CapturePath.MIN_ACCESSIBILITY_SCREENSHOT_SDK) {
            cb.onScreenshot(null);
            return;
        }
        captureWithAccessibility(service, cb);
    }

    private static void captureWithAccessibility(AccessibilityService service,
                                                 Screenshotter.ScreenshotCallback cb) {
        AccessibilityScreenshot.take(service, cb);
    }
}
