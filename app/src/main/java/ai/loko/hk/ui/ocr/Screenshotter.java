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
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 *
 */

package ai.loko.hk.ui.ocr;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;

import java.nio.Buffer;

import ai.loko.hk.ui.utils.Logger;


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Screenshotter implements ImageReader.OnImageAvailableListener {

    private static final String TAG = "Screenshotter";
    private Screenshotter mInstance;
    private Context context;
    private VirtualDisplay virtualDisplay;
    private int width;
    private int height;
    private ScreenshotCallback cb;
    private ImageReader mImageReader;
    private MediaProjection mMediaProjection;

    public Screenshotter(Context context) {
        this.context = context;
    }

    /**
     * Take screenshot.
     *
     * @param cb the cb
     */
    public void takeScreenshot(final ScreenshotCallback cb) {
        this.cb = cb;
        mImageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1);
        // The projection is owned by MediaProjectionHelper and stays alive for
        // the whole capture session; each capture only creates a fresh virtual
        // display on top of it. (Re-acquiring the consent token after stopping
        // the projection - the old behaviour - is what made captures fail
        // after a while on newer Android versions.)
        mMediaProjection = MediaProjectionHelper.getMediaProjection(context);
        if (mMediaProjection == null) {
            Log.e(TAG, "MediaProjection null. Cannot take the screenshot.");
            mImageReader = null;
            deliverResult(null);
            return;
        }
        try {
            virtualDisplay = mMediaProjection.createVirtualDisplay(
                    "Screenshotter",
                    width, height, 50,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    mImageReader.getSurface(),
                    null,
                    null);
            mImageReader.setOnImageAvailableListener(Screenshotter.this, null);
        } catch (Exception e) {
            e.printStackTrace();
            Logger.logException(e);
            tearDown();
            deliverResult(null);
        }

    }

    /**
     * Set the size of the screenshot to be taken
     *
     * @param width  width of the requested bitmap
     * @param height height of the request bitmap
     * @return the singleton instance
     */
    public Screenshotter setSize(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    @Override
    public void onImageAvailable(ImageReader reader) {
        // Detach the listener first thing. The virtual display keeps mirroring the
        // screen and further frames can already be latched in the reader while the
        // first frame is being converted; without detaching, the reader fired this
        // callback a second time for the same tap and the screenshot was saved
        // twice (~one refresh cycle apart, both files identical).
        reader.setOnImageAvailableListener(null, null);

        Bitmap bitmap = null;
        try {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                try {
                    final Image.Plane[] planes = image.getPlanes();
                    final Buffer buffer = planes[0].getBuffer().rewind();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * width;
                    bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);
                } finally {
                    image.close();
                }
            } else {
                Log.d(TAG, "onImageAvailable: image is null");
            }
        } catch (Exception e) {
            Logger.logException(e);
            bitmap = null;
        } finally {
            tearDown();
        }

        deliverResult(bitmap);
    }

    /**
     * Delivers the result of one takeScreenshot() call exactly once: the stored
     * callback is cleared before it is invoked so a stray second frame delivery
     * can never trigger the callback (and therefore a second save) again.
     */
    private void deliverResult(Bitmap bitmap) {
        ScreenshotCallback callback = cb;
        cb = null;
        if (callback != null) {
            callback.onScreenshot(bitmap);
        }
    }

    private void tearDown() {
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Exception e) {
                Logger.logException(e);
            }
            virtualDisplay = null;
        }
        // The MediaProjection itself is intentionally NOT stopped here: it
        // belongs to the capture session and is reused by the next capture.
        // It is stopped when the service is destroyed, see
        // MediaProjectionHelper.stopMediaProjection().
        mMediaProjection = null;
        mImageReader = null;
    }

    /**
     * The interface Screenshot callback.
     */
    public interface ScreenshotCallback {
        void onScreenshot(Bitmap bitmap);
    }
}
