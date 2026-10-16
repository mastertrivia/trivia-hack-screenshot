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
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ai.loko.hk.ui.MainActivity;
import ai.loko.hk.ui.constants.Constant;
import ai.loko.hk.ui.ocr.MediaProjectionHelper;
import ai.loko.hk.ui.ocr.Points;
import ai.loko.hk.ui.ocr.Screenshotter;
import ai.loko.hk.ui.utils.Logger;
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

    /**
     * Upper bound for taps that arrive while a capture is already running. Each
     * accepted tap produces exactly one screenshot; the queue only decides the
     * order in which they are processed.
     */
    private static final int MAX_PENDING_CAPTURES = 20;

    /** How long the green "screenshot saved" border stays visible after the checkmark. */
    private static final int CONFIRMED_BORDER_DURATION_MS = 1750;

    /**
     * True while the floating button is on the screen. Read by the home screen
     * (same process) to show the START/STOP SCREENSHOT button in the correct state.
     */
    private static volatile boolean sRunning = false;

    private WindowManager mWindowManager;
    private View mFloatingView;
    /**
     * The round button view that actually carries the button background.
     * The inflated layout is attached to a wrapper LinearLayout that is added
     * to the window manager (see onCreate), so mFloatingView is only that
     * invisible wrapper; setting a background on it would paint behind the
     * round child and never be visible. The state backgrounds (ready/success/
     * saved) must be set on this view.
     */
    private View mButtonView;
    private ImageView mIconView;
    private WindowManager.LayoutParams params;
    private NotificationManagerCompat notificationManager;
    private Screenshotter screenshotter;
    private int width, height;
    private final int[] coordinate = new int[4];
    private volatile boolean capturing = false;
    /** Whether the floating window is currently removed from the window manager. */
    private boolean mIconHidden = false;
    /** True while the red button shows the green "last screenshot saved" border. */
    private boolean mLastSaveConfirmed = false;
    /** Taps that arrived while a capture was running and still have to be executed. */
    private int mPendingCaptures = 0;
    /** Saves run one after another so two captures can never write at once. */
    private final ExecutorService saveExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * End of the checkmark phase: the camera icon returns on the red button and
     * the border turns green for a while, showing that the last screenshot was
     * really saved.
     */
    private final Runnable mCheckmarkEndRunnable = new Runnable() {
        @Override
        public void run() {
            if (mIconView != null) {
                mIconView.setImageResource(R.drawable.ic_camera_white);
                mIconView.animate().cancel();
                mIconView.setScaleX(1f);
                mIconView.setScaleY(1f);
                mIconView.setAlpha(1f);
            }
            mLastSaveConfirmed = true;
            if (mButtonView != null) {
                mButtonView.setBackgroundResource(R.drawable.bg_floating_button_saved);
            }
            mainHandler.postDelayed(mBorderResetRunnable, CONFIRMED_BORDER_DURATION_MS);
        }
    };

    /**
     * End of the green-border confirmation phase: back to the ready state
     * (red camera button, white border).
     */
    private final Runnable mBorderResetRunnable = new Runnable() {
        @Override
        public void run() {
            mLastSaveConfirmed = false;
            if (mButtonView != null) {
                mButtonView.setBackgroundResource(R.drawable.bg_floating_button);
            }
        }
    };

    /**
     * Whether the floating screenshot button is currently on the screen.
     */
    public static boolean isRunning() {
        return sRunning;
    }

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

        sRunning = true;
        notificationManager = NotificationManagerCompat.from(this);
        mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        mFloatingView = LayoutInflater.from(this).inflate(R.layout.floating, new LinearLayout(this));
        mButtonView = mFloatingView.findViewById(R.id.head);
        mIconView = mFloatingView.findViewById(R.id.icon);
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
        final View head = mButtonView;
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
     * <p>
     * One accepted tap produces exactly one screenshot. Taps that arrive while a
     * capture is already running are queued (bounded) and executed right after the
     * current capture, so rapid consecutive taps all go through without merging
     * and without duplicates.
     */
    private void captureScreenshot() {
        if (capturing) {
            if (mPendingCaptures < MAX_PENDING_CAPTURES) {
                mPendingCaptures++;
            }
            return;
        }
        startCapture();
    }

    private void startCapture() {
        if (MediaProjectionHelper.screenshotPermission == null) {
            mPendingCaptures = 0;
            showToast("Screen capture permission missing, tap START SCREENSHOT again");
            return;
        }
        capturing = true;
        // Remove the floating window from the screen before the virtual display
        // starts mirroring so the icon can never end up in the screenshot. The
        // window (with its position and size) is added back the moment the frame
        // has been acquired.
        hideIconForCapture();
        try {
            screenshotter.takeScreenshot(new Screenshotter.ScreenshotCallback() {
                @Override
                public void onScreenshot(final Bitmap bitmap) {
                    // Runs on the main thread, exactly once per accepted tap.
                    if (!sRunning) {
                        // The service was stopped (notification or home screen
                        // toggle) while this capture was in flight.
                        return;
                    }
                    showIconAfterCapture();
                    if (bitmap == null) {
                        // Capture failed, for example because the capture
                        // permission was revoked.
                        mPendingCaptures = 0;
                        capturing = false;
                        showToast("Screenshot failed, tap START SCREENSHOT again");
                        return;
                    }
                    saveExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (!isScreenshotSavingEnabled()) {
                                // Nothing is written, so there is nothing to
                                // confirm: no checkmark without a saved file.
                                showToast("Screenshot taken, saving is disabled in Settings");
                                return;
                            }
                            if (Utils.writeToStorage(getApplicationContext(), cropToProfileRegion(bitmap))) {
                                // Only a really saved screenshot gets the green
                                // checkmark confirmation.
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        playCaptureFeedback();
                                    }
                                });
                            } else {
                                showToast("Screenshot could not be saved");
                            }
                        }
                    });
                    // Continue with the next queued tap immediately, without waiting
                    // for the save to finish (saving runs on its own thread).
                    if (mPendingCaptures > 0) {
                        mPendingCaptures--;
                        startCapture();
                    } else {
                        capturing = false;
                    }
                }
            });
        } catch (Exception e) {
            // Capture failed, for example because the capture permission was revoked.
            Logger.logException(e);
            mPendingCaptures = 0;
            capturing = false;
            if (sRunning) {
                showIconAfterCapture();
            }
            showToast("Screenshot failed, tap START SCREENSHOT again");
        }
    }

    /**
     * Removes the floating window from the screen immediately, before the capture
     * starts, so the icon cannot appear in the screenshot.
     */
    private void hideIconForCapture() {
        mainHandler.removeCallbacks(mCheckmarkEndRunnable);
        mainHandler.removeCallbacks(mBorderResetRunnable);
        if (mIconView != null) {
            mIconView.animate().cancel();
            mIconView.setScaleX(1f);
            mIconView.setScaleY(1f);
            mIconView.setAlpha(1f);
            // Drop a checkmark that is still on screen: the icon must never come
            // back showing a tick while the next capture is still in progress.
            mIconView.setImageResource(R.drawable.ic_camera_white);
        }
        if (mButtonView != null) {
            mButtonView.setBackgroundResource(mLastSaveConfirmed
                    ? R.drawable.bg_floating_button_saved
                    : R.drawable.bg_floating_button);
        }
        if (!mIconHidden && mWindowManager != null && mFloatingView != null
                && mFloatingView.isAttachedToWindow()) {
            // removeViewImmediate guarantees the window is gone before this call
            // returns, i.e. before the virtual display mirrors the screen.
            mWindowManager.removeViewImmediate(mFloatingView);
            mIconHidden = true;
        }
    }

    /**
     * Puts the floating window back (same position and size) as soon as the
     * frame was acquired. No confirmation is played here: the checkmark means
     * "screenshot saved" and is only shown after the file really is on storage
     * (see the save task in {@link #startCapture()}).
     */
    private void showIconAfterCapture() {
        if (mIconHidden && sRunning && mWindowManager != null && mFloatingView != null
                && !mFloatingView.isAttachedToWindow()) {
            mWindowManager.addView(mFloatingView, params);
            mIconHidden = false;
        }
    }

    /**
     * Confirmation played after a screenshot was really written to storage: the
     * red button turns green with a white checkmark and a quick pop (~340ms),
     * then the camera returns on a red button whose border stays green for
     * ~1.75s before the white ready border comes back. The animation never
     * blocks taps; a new capture simply cancels and restarts it.
     */
    private void playCaptureFeedback() {
        if (!sRunning || mIconView == null) {
            return;
        }
        mainHandler.removeCallbacks(mCheckmarkEndRunnable);
        mainHandler.removeCallbacks(mBorderResetRunnable);
        if (mButtonView != null) {
            mButtonView.setBackgroundResource(R.drawable.bg_floating_button_success);
        }
        mIconView.setImageResource(R.drawable.ic_check_white);
        mIconView.animate().cancel();
        mIconView.setScaleX(0.65f);
        mIconView.setScaleY(0.65f);
        mIconView.setAlpha(0.5f);
        mIconView.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(160)
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();
        mainHandler.postDelayed(mCheckmarkEndRunnable, 340);
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
        sRunning = false;
        saveExecutor.shutdown();
        mainHandler.removeCallbacks(mCheckmarkEndRunnable);
        mainHandler.removeCallbacks(mBorderResetRunnable);
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(sizeListener);
        if (mFloatingView != null && mWindowManager != null && mFloatingView.isAttachedToWindow()) {
            mWindowManager.removeView(mFloatingView);
        }
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
