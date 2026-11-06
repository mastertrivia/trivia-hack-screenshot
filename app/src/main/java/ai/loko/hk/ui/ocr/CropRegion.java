package ai.loko.hk.ui.ocr;

/**
 * Maps a profile crop rectangle from screen coordinates onto a captured
 * bitmap. Accessibility screenshots can be larger than the window size used
 * when the profile was saved (status bar, cutouts, density).
 * <p>
 * Returns {@code null} when the whole bitmap should be kept.
 */
public final class CropRegion {

    private CropRegion() {
    }

    /**
     * @return {@code {x, y, width, height}} or {@code null} for the full bitmap
     */
    public static int[] map(int x1, int y1, int x2, int y2,
                            int screenWidth, int screenHeight,
                            int bitmapWidth, int bitmapHeight) {
        if (x2 == 0 || y2 == 0) {
            return null;
        }
        if (screenWidth > 0 && screenHeight > 0
                && (bitmapWidth != screenWidth || bitmapHeight != screenHeight)) {
            x1 = x1 * bitmapWidth / screenWidth;
            y1 = y1 * bitmapHeight / screenHeight;
            x2 = x2 * bitmapWidth / screenWidth;
            y2 = y2 * bitmapHeight / screenHeight;
        }
        if (x1 < 0) {
            x1 = 0;
        }
        if (y1 < 0) {
            y1 = 0;
        }
        if (x2 > bitmapWidth) {
            x2 = bitmapWidth;
        }
        if (y2 > bitmapHeight) {
            y2 = bitmapHeight;
        }
        if (x2 <= x1 || y2 <= y1) {
            return null;
        }
        return new int[]{x1, y1, x2 - x1, y2 - y1};
    }
}
