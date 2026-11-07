package ai.loko.hk.ui.ocr;

/**
 * Error codes from {@code AccessibilityService.takeScreenshot()}. Values match
 * the platform constants so tests can run without the Android SDK.
 */
public final class ScreenshotError {

    public static final int INTERNAL_ERROR = 1;
    public static final int NO_ACCESSIBILITY_ACCESS = 2;
    public static final int INTERVAL_TIME_SHORT = 3;
    public static final int INVALID_DISPLAY = 4;

    private ScreenshotError() {
    }

    public static boolean shouldRetry(int errorCode) {
        return errorCode == INTERVAL_TIME_SHORT;
    }
}
