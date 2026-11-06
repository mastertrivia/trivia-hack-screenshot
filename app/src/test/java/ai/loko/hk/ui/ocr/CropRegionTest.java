package ai.loko.hk.ui.ocr;

public class CropRegionTest {

    private static int failures = 0;

    private static void assertRect(String name, int[] expected, int[] actual) {
        boolean same = expected.length == actual.length;
        if (same) {
            for (int i = 0; i < expected.length; i++) {
                if (expected[i] != actual[i]) {
                    same = false;
                    break;
                }
            }
        }
        if (!same) {
            System.err.println("FAIL " + name
                    + ": expected " + java.util.Arrays.toString(expected)
                    + " but got " + java.util.Arrays.toString(actual));
            failures++;
        } else {
            System.out.println("OK   " + name);
        }
    }

    private static void assertNull(String name, int[] actual) {
        if (actual != null) {
            System.err.println("FAIL " + name + ": expected full bitmap");
            failures++;
        } else {
            System.out.println("OK   " + name);
        }
    }

    public static void main(String[] args) {
        fullScreenWhenRegionUnset();
        sameSizeKeepsCoordinates();
        scalesWhenBitmapIsLarger();
        clampsToBitmap();
        invalidRegionMeansFullBitmap();

        if (failures > 0) {
            System.err.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("All tests passed");
    }

    private static void fullScreenWhenRegionUnset() {
        assertNull("unset region", CropRegion.map(0, 0, 0, 0, 1080, 1920, 1080, 1920));
    }

    private static void sameSizeKeepsCoordinates() {
        assertRect("same size",
                new int[]{10, 20, 100, 200},
                CropRegion.map(10, 20, 110, 220, 1080, 1920, 1080, 1920));
    }

    private static void scalesWhenBitmapIsLarger() {
        assertRect("scale 2x",
                new int[]{20, 40, 180, 360},
                CropRegion.map(10, 20, 110, 220, 100, 200, 200, 400));
    }

    private static void clampsToBitmap() {
        assertRect("clamp",
                new int[]{0, 0, 100, 200},
                CropRegion.map(-10, -10, 1000, 2000, 100, 200, 100, 200));
    }

    private static void invalidRegionMeansFullBitmap() {
        assertNull("inverted", CropRegion.map(50, 50, 10, 10, 100, 100, 100, 100));
    }
}
