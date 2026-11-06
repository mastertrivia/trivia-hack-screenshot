package ai.loko.hk.ui.ocr;

/**
 * Picks the screenshot engine for one floating-button tap.
 * <p>
 * Android 11+ ({@code API 30}) can capture through
 * {@code AccessibilityService.takeScreenshot()} without a MediaProjection
 * session. That is what stops Android 14+ from asking "Start recording or
 * casting?" on every tap. Older devices, and devices where accessibility is
 * off, keep the existing MediaProjection path.
 */
public final class CapturePath {

    public static final int NONE = 0;
    public static final int MEDIA_PROJECTION = 1;
    public static final int ACCESSIBILITY = 2;

    /** First API that has {@code AccessibilityService.takeScreenshot()}. */
    public static final int MIN_ACCESSIBILITY_SCREENSHOT_SDK = 30;

    private CapturePath() {
    }

    /**
     * @param sdkInt                     {@code Build.VERSION.SDK_INT}
     * @param accessibilityRunning       the declared accessibility service is connected
     * @param mediaProjectionPermission  a MediaProjection consent token is held
     */
    public static int choose(int sdkInt, boolean accessibilityRunning,
                             boolean mediaProjectionPermission) {
        if (sdkInt >= MIN_ACCESSIBILITY_SCREENSHOT_SDK && accessibilityRunning) {
            return ACCESSIBILITY;
        }
        if (mediaProjectionPermission) {
            return MEDIA_PROJECTION;
        }
        return NONE;
    }

    /**
     * START SCREENSHOT can skip Android's screen-share consent dialog when
     * accessibility screenshots are available.
     */
    public static boolean canStartWithoutMediaProjection(int sdkInt,
                                                         boolean accessibilityEnabled) {
        return sdkInt >= MIN_ACCESSIBILITY_SCREENSHOT_SDK && accessibilityEnabled;
    }
}
