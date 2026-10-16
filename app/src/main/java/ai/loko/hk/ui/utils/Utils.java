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

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import ai.loko.hk.ui.constants.Constant;

public class Utils {

    /** Last timestamp used for a screenshot file name (kept monotonic). */
    private static long lastScreenshotTimestamp = 0L;

    /**
     * Saves a captured screenshot to the shared Pictures folder
     * (/Pictures/TriviaHacK), creating the folder if needed. The previously
     * existing helpers for OCR image preparation and for saving the extracted
     * question/options as a text log were removed together with OCR and answer
     * search.
     *
     * @param context used to notify the media scanner so the image shows up in
     *                the gallery and file manager right away
     * @param bmp     the screenshot to save
     * @return true when the file was written
     */
    public static boolean writeToStorage(Context context, Bitmap bmp) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        // Fast consecutive captures can finish within the same millisecond; keep
        // the timestamp strictly increasing so two screenshots never share a
        // file name (a shared name would silently overwrite the first file).
        long timestamp;
        synchronized (Utils.class) {
            timestamp = System.currentTimeMillis();
            if (timestamp <= lastScreenshotTimestamp) {
                timestamp = lastScreenshotTimestamp + 1;
            }
            lastScreenshotTimestamp = timestamp;
        }
        File dir = new File(Constant.PATH);
        if (!dir.exists() && !dir.mkdirs()) {
            // Fall through: the write below will fail and be reported.
        }
        File picFile = new File(dir, "SCR_" + Long.toString(timestamp) + ".jpg");
        bmp.compress(Bitmap.CompressFormat.JPEG, 60, bytes);
        try {
            picFile.createNewFile();
            FileOutputStream outputStream = new FileOutputStream(picFile);
            outputStream.write(bytes.toByteArray());
            outputStream.close();
            // Make the new screenshot visible in the gallery/file manager.
            // The MediaStore row is inserted directly and synchronously; this
            // works on every supported Android version and does not depend on
            // the media scanner service, which some OEM skins (e.g. MIUI)
            // delay or ignore for background requests. The scanner call below
            // stays as a fallback and refreshes the row metadata.
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DATA, picFile.getAbsolutePath());
                values.put(MediaStore.Images.Media.TITLE, picFile.getName());
                values.put(MediaStore.Images.Media.DISPLAY_NAME, picFile.getName());
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000);
                values.put(MediaStore.Images.Media.DATE_TAKEN, timestamp);
                context.getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            } catch (Exception e) {
                Logger.logException(e);
            }
            MediaScannerConnection.scanFile(context,
                    new String[]{picFile.getAbsolutePath()},
                    new String[]{"image/jpeg"}, null);
            return true;
        } catch (IOException e) {
            Logger.logException(e);
            return false;
        }
    }
}
