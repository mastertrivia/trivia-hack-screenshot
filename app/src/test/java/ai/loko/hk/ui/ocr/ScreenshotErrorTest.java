package ai.loko.hk.ui.ocr;

public class ScreenshotErrorTest {

    private static int failures = 0;

    private static void assertTrue(String name, boolean actual) {
        if (!actual) {
            System.err.println("FAIL " + name + ": expected true");
            failures++;
        } else {
            System.out.println("OK   " + name);
        }
    }

    private static void assertFalse(String name, boolean actual) {
        if (actual) {
            System.err.println("FAIL " + name + ": expected false");
            failures++;
        } else {
            System.out.println("OK   " + name);
        }
    }

    public static void main(String[] args) {
        rateLimitIsRetried();
        accessDeniedIsNotRetried();
        internalErrorIsNotRetried();
        invalidDisplayIsNotRetried();
        unknownErrorIsNotRetried();

        if (failures > 0) {
            System.err.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("All tests passed");
    }

    private static void rateLimitIsRetried() {
        assertTrue("interval too short",
                ScreenshotError.shouldRetry(ScreenshotError.INTERVAL_TIME_SHORT));
    }

    private static void accessDeniedIsNotRetried() {
        assertFalse("no accessibility access",
                ScreenshotError.shouldRetry(ScreenshotError.NO_ACCESSIBILITY_ACCESS));
    }

    private static void internalErrorIsNotRetried() {
        assertFalse("internal error",
                ScreenshotError.shouldRetry(ScreenshotError.INTERNAL_ERROR));
    }

    private static void invalidDisplayIsNotRetried() {
        assertFalse("invalid display",
                ScreenshotError.shouldRetry(ScreenshotError.INVALID_DISPLAY));
    }

    private static void unknownErrorIsNotRetried() {
        assertFalse("unknown", ScreenshotError.shouldRetry(99));
    }
}
