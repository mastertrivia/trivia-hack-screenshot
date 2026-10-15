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

package ai.loko.hk.ui.services;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import ai.loko.hk.ui.MainActivity;
import ai.loko.hk.ui.constants.Constant;
import ai.loko.hk.ui.ocr.MediaProjectionHelper;
import ai.loko.hk.ui.ocr.Points;
import ai.loko.hk.ui.ocr.Screenshotter;
import ai.loko.hk.ui.utils.Utils;
import ui.R;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE;

/**
 * The floating screenshot button.
 * <p>
 * Shows one small round, draggable button on top of other apps. Tapping it captures the
 * screen (the full screen, or the region of the profile that was selected in the profile
 * screen) and saves the screenshot to storage, exactly like the old "Get Answer" button
 * did before OCR and answer searching were removed.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class Floating extends Service {

    /**
     * Shared preference key for the floating button size. The stored value is the offset
     * in dp added to {@link #BUTTON_SIZE_MIN_DP} (SeekBarPreference cannot store a minimum
     * value on old Android versions, so an offset keeps the range valid on every device).
     */
    private static final String PREF_BUTTON_SIZE = Constant.FLOATING_BUTTON_SIZE;
    private static final int BUTTON_SIZE_MIN_DP = Constant.FLOATING_BUTTON_SIZE_MIN;
    private static final int BUTTON_SIZE_DEFAULT_OFFSET = Constant.FLOATING_BUTTON_SIZE_DEFAULT;

    private WindowManager mWindowManager;
    private View mFloatingView;
    private WindowManager.LayoutParams params;
    private NotificationManagerCompat notificationManager;
    private Screenshotter screenshotter;
    private int width, height;
    private final int[] coordinate = new int[4];
    private volatile boolean capturing = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final SharedPreferences.OnSharedPreferenceChangeListener sizeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    if (PREF_BUTTON_SIZE.equals(key)) {
                        applyButtonSize(sharedPreferences);
                    }
                }
            };

    public Floating() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        android.os.Process.setThreadPriority(THREAD_PRIORITY_BACKGROUND + THREAD_PRIORITY_MORE_FAVORABLE);

        notificationManager = NotificationManagerCompat.from(this);
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        mFloatingView = LayoutInflater.from(this).inflate(R.layout.floating, new LinearLayout(this));
        notification();

        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        // Specify the view position: initially added to the top-left corner.
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;
        applyButtonSize(PreferenceManager.getDefaultSharedPreferences(this));

        if (mWindowManager != null) {
            mWindowManager.addView(mFloatingView, params);
        }
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(sizeListener);

        Display display = mWindowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        width = size.x;
        height = size.y;

        screenshotter = new Screenshotter(getApplicationContext());
        screenshotter.setSize(width, height);

        // Region of the selected profile; X2/Y2 == 0 means "no profile" -> full screen.
        coordinate[0] = (int) Math.ceil((double) Points.X1);
        coordinate[1] = (int) Math.ceil((double) Points.Y1);
        coordinate[2] = (int) Math.ceil((double) Points.X2);
        coordinate[3] = (int) Math.ceil((double) Points.Y2);

        setupDragAndTap();
    }

    /**
     * The button can be dragged anywhere on the screen; a tap (without moving beyond the
     * touch slop) captures the screenshot.
     */
    private void setupDragAndTap() {
        final View head = mFloatingView.findViewById(R.id.head);
        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        head.setOnTouchListener(new View.OnTouchListener() {
            int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private boolean dragging;

            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;
                        if (!dragging
                                && (Math.abs(deltaX) > touchSlop || Math.abs(deltaY) > touchSlop)) {
                            dragging = true;
                        }
                        if (dragging) {
                            // Calculate the X and Y coordinates of the view and update it.
                            params.x = initialX + (int) deltaX;
                            params.y = initialY + (int) deltaY;
                            mWindowManager.updateViewLayout(mFloatingView, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            v.performClick();
                        }
                        return true;
                }
                return false;
            }
        });

        head.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureScreenshot();
            }
        });
    }

    /**
     * Captures the screen and saves it, using the same screenshot engine the old
     * "Get Answer" button used. No OCR and no answer search is performed.
     */
    private void captureScreenshot() {
        if (capturing) {
            return;
        }
        if (MediaProjectionHelper.screenshotPermission == null) {
            Toast.makeText(this, "Screen capture permission missing, tap START SCREENSHOT again",
                    Toast.LENGTH_LONG).show();
            return;
        }
        capturing = true;
        try {
            screenshotter.takeScreenshot(new Screenshotter.ScreenshotCallback() {
                @Override
                public void onScreenshot(final Bitmap bitmap) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            boolean saved = false;
                            if (isScreenshotSavingEnabled()) {
                                Utils.writeToStorage(cropToProfileRegion(bitmap));
                                saved = true;
                            }
                            capturing = false;
                            showToast(saved
                                    ? "Screenshot saved"
                                    : "Screenshot taken, saving is disabled in Settings");
                        }
                    }).start();
                }
            });
        } catch (Exception e) {
            // Capture failed, for example because the capture permission was revoked.
            capturing = false;
            showToast("Screenshot failed, tap START SCREENSHOT again");
        }
    }

    /**
     * Whether screenshots should be written to storage (Settings -&gt; Logs/Debug Settings).
     */
    private boolean isScreenshotSavingEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(getString(R.string.save_image_and_file_to_storage_key), true);
    }

    private void showToast(final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(Floating.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Crops the captured frame to the selected profile region; falls back to the complete
     * screen when no profile region was set (full screen capture).
     */
    private Bitmap cropToProfileRegion(Bitmap bitmap) {
        int x1 = coordinate[0];
        int y1 = coordinate[1];
        int x2 = coordinate[2];
        int y2 = coordinate[3];
        if (x2 == 0 || y2 == 0) {
            x2 = width;
            y2 = height;
        }
        if (x1 < 0 || y1 < 0 || x2 <= x1 || y2 <= y1
                || x2 > bitmap.getWidth() || y2 > bitmap.getHeight()) {
            return bitmap;
        }
        return Bitmap.createBitmap(bitmap, x1, y1, x2 - x1, y2 - y1);
    }

    /**
     * Resizes the floating button according to the "Floating button size" setting.
     */
    private void applyButtonSize(SharedPreferences sharedPreferences) {
        if (params == null) {
            return;
        }
        int offset = sharedPreferences.getInt(PREF_BUTTON_SIZE, BUTTON_SIZE_DEFAULT_OFFSET);
        if (offset < 0) offset = 0;
        if (offset > 100) offset = 100;
        int sizeInPx = (int) ((BUTTON_SIZE_MIN_DP + offset)
                * getResources().getDisplayMetrics().density + 0.5f);
        params.width = sizeInPx;
        params.height = sizeInPx;
        if (mFloatingView != null && mFloatingView.isAttachedToWindow()) {
            mWindowManager.updateViewLayout(mFloatingView, params);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = null;
        if (intent != null)
            action = intent.getAction();
        if (action != null && action.equalsIgnoreCase("stop")) {
            stopSelf();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(sizeListener);
        if (mFloatingView != null && mWindowManager != null) mWindowManager.removeView(mFloatingView);
        if (notificationManager != null) notificationManager.cancelAll();
    }

    private void notification() {
        Intent i = new Intent(this, Floating.class);
        i.setAction("stop");
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext(), "stop");
        mBuilder.setContentText("Tap the floating button to take a screenshot")
                .setContentTitle("Tap to remove floating button")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setOngoing(true).setAutoCancel(true)
                .addAction(android.R.drawable.ic_menu_more, "Open Trivia Hack", pendingIntent);

        notificationManager.notify(1234, mBuilder.build());
    }
}
