package ai.loko.hk.ui.ocr;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;

import java.util.concurrent.Executor;

import ai.loko.hk.ui.utils.Logger;

/**
 * Android 11+ screenshot path. Isolated so older devices never load
 * {@link HardwareBuffer} / {@code takeScreenshot} when the accessibility
 * service class is initialized.
 */
@TargetApi(30)
public final class AccessibilityScreenshot {

    private static final Executor MAIN_EXECUTOR = new Executor() {
        private final Handler handler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                command.run();
            } else {
                handler.post(command);
            }
        }
    };

    private AccessibilityScreenshot() {
    }

    public static void take(AccessibilityService service,
                            final Screenshotter.ScreenshotCallback cb) {
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, MAIN_EXECUTOR,
                    new AccessibilityService.TakeScreenshotCallback() {
                        @Override
                        public void onSuccess(AccessibilityService.ScreenshotResult screenshot) {
                            cb.onScreenshot(bitmapFrom(screenshot));
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            cb.onScreenshot(null);
                        }
                    });
        } catch (Exception e) {
            Logger.logException(e);
            cb.onScreenshot(null);
        }
    }

    private static Bitmap bitmapFrom(AccessibilityService.ScreenshotResult screenshot) {
        HardwareBuffer buffer = screenshot.getHardwareBuffer();
        if (buffer == null) {
            return null;
        }
        Bitmap wrapped = null;
        try {
            wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.getColorSpace());
            if (wrapped == null) {
                return null;
            }
            return wrapped.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Exception e) {
            Logger.logException(e);
            return null;
        } finally {
            if (wrapped != null) {
                wrapped.recycle();
            }
            buffer.close();
        }
    }
}
