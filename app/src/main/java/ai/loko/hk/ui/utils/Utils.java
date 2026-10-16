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

package ai.loko.hk.ui.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

import ai.loko.hk.ui.constants.Constant;

public class Utils {

    private static final String TAG = "Utils";

    /** Folder inside the shared Pictures directory that keeps all screenshots. */
    private static final String SCREENSHOT_DIRECTORY = "TriviaHacK";
    private static final String MIME_TYPE_JPEG = "image/jpeg";
    private static final int JPEG_QUALITY = 60;

    /** Last timestamp used for a screenshot file name (kept monotonic). */
    private static long lastScreenshotTimestamp = 0L;

    /**
     * Saves a captured screenshot to the shared Pictures folder
     * (/Pictures/TriviaHacK) and makes sure it is registered with the
     * MediaStore so it shows up normally in the gallery (including the gallery
     * edit/crop/share actions, which require a real MediaStore row).
     * <p>
     * The correct API depends on the Android version:
     * <ul>
     *     <li>Android 10 (API 29) and newer: the image is inserted into
     *     MediaStore with {@code RELATIVE_PATH = Pictures/TriviaHacK} and
     *     written while {@code IS_PENDING = 1}. The row is only published
     *     ({@code IS_PENDING = 0}) after the JPEG was encoded and the stream was
     *     closed successfully; an incomplete row is deleted again.</li>
     *     <li>Older Android: the file is written to the public Pictures
     *     directory and the finished file is handed to the media scanner, which
     *     creates the MediaStore row (no direct insert, so no duplicate rows).</li>
     * </ul>
     *
     * @param context used to reach the ContentResolver / media scanner
     * @param bmp     the screenshot to save
     * @return true only when the file was written and registered successfully
     */
    public static boolean writeToStorage(Context context, Bitmap bmp) {
        if (context == null || bmp == null) {
            Log.e(TAG, "writeToStorage: context or bitmap is null, nothing to save");
            return false;
        }
        makeScreenshotFolderVisible(context);
        long timestamp = nextTimestamp();
        String fileName = "SCR_" + Long.toString(timestamp) + ".jpg";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return saveWithMediaStore(context, bmp, fileName, timestamp);
        }
        return saveToPicturesAndScan(context, bmp, fileName, timestamp);
    }

    /**
     * Guards against a stray {@code .nomedia} marker inside the app's own
     * screenshot folder. A single {@code .nomedia} file makes the media scanner
     * and every gallery skip the whole directory, so valid JPEGs stay invisible
     * (they open in a file manager but have no MediaStore row and no
     * edit/crop/share actions). The folder belongs to the app, so removing a
     * marker that is hiding the app's own screenshots is safe.
     * <p>
     * When a marker is found it is deleted and the folder is re-scanned once so
     * screenshots that were already saved while it was hidden become visible too.
     */
    private static void makeScreenshotFolderVisible(Context context) {
        File dir = new File(Constant.PATH);
        File noMedia = new File(dir, ".nomedia");
        if (!noMedia.exists()) {
            return;
        }
        if (noMedia.delete()) {
            Log.w(TAG, "Removed stray .nomedia that hid " + dir.getAbsolutePath());
            rescanScreenshotFolder(context, dir);
        } else {
            Log.e(TAG, "Found .nomedia in " + dir.getAbsolutePath() + " but could not delete it");
        }
    }

    /**
     * Hands every file that is already in the screenshot folder to the media
     * scanner once. Used after a stray {@code .nomedia} marker was removed so
     * screenshots written while the folder was hidden get a MediaStore row.
     */
    private static void rescanScreenshotFolder(Context context, File dir) {
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        String[] paths = new String[files.length];
        String[] mimeTypes = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            paths[i] = files[i].getAbsolutePath();
            mimeTypes[i] = files[i].getName().toLowerCase().endsWith(".jpg")
                    ? MIME_TYPE_JPEG
                    : null;
        }
        MediaScannerConnection.scanFile(context, paths, mimeTypes, null);
    }

    /**
     * Android 10+ save path: the whole file lives behind a MediaStore row from
     * the very first byte. This is the supported way to create shared images and
     * it is what makes the gallery treat the file as a normal, editable image.
     */
    private static boolean saveWithMediaStore(Context context, Bitmap bmp,
                                              String fileName, long timestamp) {
        final ContentResolver resolver = context.getContentResolver();

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE_JPEG);
        values.put(MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + File.separator + SCREENSHOT_DIRECTORY);
        values.put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000);
        values.put(MediaStore.Images.Media.DATE_TAKEN, timestamp);
        // Keep the unfinished file private to other apps until it is complete.
        values.put(MediaStore.Images.Media.IS_PENDING, 1);

        Uri uri = null;
        OutputStream outputStream = null;
        boolean written = false;
        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Log.e(TAG, "MediaStore insert returned null for " + fileName);
            } else {
                outputStream = resolver.openOutputStream(uri);
                if (outputStream == null) {
                    Log.e(TAG, "MediaStore openOutputStream returned null for " + uri);
                } else if (bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)) {
                    outputStream.flush();
                    written = true;
                } else {
                    Log.e(TAG, "Bitmap.compress returned false for " + uri);
                }
            }
        } catch (Exception e) {
            Logger.logException(e);
            Log.e(TAG, "Saving screenshot through MediaStore failed for " + fileName, e);
        } finally {
            closeQuietly(outputStream);
        }

        if (!written) {
            // Never keep a pending row without a complete image behind it.
            deletePending(resolver, uri);
            return false;
        }

        // The JPEG is complete and the stream is closed: publish the row so the
        // gallery, file manager and editing apps can access it.
        boolean published = false;
        try {
            ContentValues publish = new ContentValues();
            publish.put(MediaStore.Images.Media.IS_PENDING, 0);
            published = resolver.update(uri, publish, null, null) > 0;
        } catch (Exception e) {
            Logger.logException(e);
            Log.e(TAG, "Publishing screenshot failed for " + uri, e);
        }

        if (!published) {
            Log.e(TAG, "Screenshot was not published, deleting incomplete row " + uri);
            deletePending(resolver, uri);
        }
        return published;
    }

    /**
     * Android 9 and older save path: write the finished JPEG to the public
     * Pictures directory and trigger a media scan afterwards. The scanner is the
     * single registration step here, so no duplicate MediaStore rows are created.
     */
    private static boolean saveToPicturesAndScan(Context context, Bitmap bmp,
                                                 String fileName, long timestamp) {
        File dir = new File(Constant.PATH);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            Log.e(TAG, "Could not create screenshot directory " + dir);
            return false;
        }
        File picFile = new File(dir, fileName);
        OutputStream outputStream = null;
        boolean written = false;
        try {
            outputStream = new FileOutputStream(picFile);
            if (bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)) {
                outputStream.flush();
                written = true;
            } else {
                Log.e(TAG, "Bitmap.compress returned false for " + picFile);
            }
        } catch (Exception e) {
            Logger.logException(e);
            Log.e(TAG, "Writing screenshot failed for " + picFile, e);
        } finally {
            closeQuietly(outputStream);
        }

        if (!written) {
            // Do not leave a partial file behind.
            if (picFile.exists() && !picFile.delete()) {
                Log.e(TAG, "Could not delete incomplete screenshot " + picFile);
            }
            return false;
        }

        // Register the completed file with the MediaStore. scanFile creates the
        // row itself; adding a manual insert here would duplicate it.
        MediaScannerConnection.scanFile(context,
                new String[]{picFile.getAbsolutePath()},
                new String[]{MIME_TYPE_JPEG}, null);
        return true;
    }

    private static void deletePending(ContentResolver resolver, Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            resolver.delete(uri, null, null);
        } catch (Exception e) {
            Logger.logException(e);
            Log.e(TAG, "Could not delete incomplete MediaStore row " + uri, e);
        }
    }

    private static void closeQuietly(OutputStream outputStream) {
        if (outputStream == null) {
            return;
        }
        try {
            outputStream.close();
        } catch (Exception e) {
            Log.e(TAG, "Closing the output stream failed", e);
        }
    }

    /**
     * Fast consecutive captures can finish within the same millisecond; keep
     * the timestamp strictly increasing so two screenshots never share a
     * file name (a shared name would silently overwrite the first file).
     */
    private static synchronized long nextTimestamp() {
        long timestamp = System.currentTimeMillis();
        if (timestamp <= lastScreenshotTimestamp) {
            timestamp = lastScreenshotTimestamp + 1;
        }
        lastScreenshotTimestamp = timestamp;
        return timestamp;
    }
}
