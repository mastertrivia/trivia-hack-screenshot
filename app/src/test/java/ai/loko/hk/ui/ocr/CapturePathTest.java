package ai.loko.hk.ui.ocr;

/**
 * Decision tests for which screenshot engine a floating-button tap should use.
 * Run: javac -cp app/src/main/java:app/src/test/java app/src/test/java/ai/loko/hk/ui/ocr/CapturePathTest.java
 *      java -cp app/src/main/java:app/src/test/java ai.loko.hk.ui.ocr.CapturePathTest
 */
public class CapturePathTest {

    private static int failures = 0;

    private static void assertEquals(String name, int expected, int actual) {
        if (expected != actual) {
            System.err.println("FAIL " + name + ": expected " + expected + " but got " + actual);
            failures++;
        } else {
            System.out.println("OK   " + name);
        }
    }

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
        android11WithAccessibilityUsesAccessibilityEngine();
        android14WithAccessibilityIgnoresMediaProjectionPermission();
        android14WithoutAccessibilityUsesMediaProjection();
        belowApi30UsesMediaProjectionEvenIfAccessibilityIsOn();
        noEngineWhenNeitherPathIsAvailable();
        startSkipsMediaProjectionConsentOnApi30WithAccessibility();
        startStillNeedsMediaProjectionWhenAccessibilityIsOff();
        startStillNeedsMediaProjectionBelowApi30();

        if (failures > 0) {
            System.err.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("All tests passed");
    }

    private static void android11WithAccessibilityUsesAccessibilityEngine() {
        assertEquals("API 30 + accessibility",
                CapturePath.ACCESSIBILITY,
                CapturePath.choose(30, true, false));
    }

    private static void android14WithAccessibilityIgnoresMediaProjectionPermission() {
        assertEquals("API 34 + accessibility must not reuse MediaProjection",
                CapturePath.ACCESSIBILITY,
                CapturePath.choose(34, true, true));
    }

    private static void android14WithoutAccessibilityUsesMediaProjection() {
        assertEquals("API 34 without accessibility",
                CapturePath.MEDIA_PROJECTION,
                CapturePath.choose(34, false, true));
    }

    private static void belowApi30UsesMediaProjectionEvenIfAccessibilityIsOn() {
        assertEquals("API 29 cannot call takeScreenshot",
                CapturePath.MEDIA_PROJECTION,
                CapturePath.choose(29, true, true));
    }

    private static void noEngineWhenNeitherPathIsAvailable() {
        assertEquals("nothing to capture with",
                CapturePath.NONE,
                CapturePath.choose(34, false, false));
    }

    private static void startSkipsMediaProjectionConsentOnApi30WithAccessibility() {
        assertTrue("START can skip the screen-share dialog",
                CapturePath.canStartWithoutMediaProjection(30, true));
    }

    private static void startStillNeedsMediaProjectionWhenAccessibilityIsOff() {
        assertFalse("START still needs MediaProjection without accessibility",
                CapturePath.canStartWithoutMediaProjection(34, false));
    }

    private static void startStillNeedsMediaProjectionBelowApi30() {
        assertFalse("START still needs MediaProjection below API 30",
                CapturePath.canStartWithoutMediaProjection(29, true));
    }
}
