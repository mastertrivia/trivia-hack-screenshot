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

package ai.loko.hk.ui.constants;

import android.os.Environment;

public class Constant {

    public static final int CODE_DRAW_OVER_OTHER_APP_PERMISSION = 2084;
    public static final int CODE_FOR_SCREEN_CAPTURE = 1349;
    public static final String CLIP_POINTS = "clipsPoints";
    public static final String PROFILE_NAME = "profileName";

    /** Shared preference key for the size of the floating screenshot button. */
    public static final String FLOATING_BUTTON_SIZE = "floating_button_size";
    /** Smallest allowed floating button size in dp. */
    public static final int FLOATING_BUTTON_SIZE_MIN = 40;
    /** Stored value that is used when the user never moved the size slider (40 + 24 = 64dp). */
    public static final int FLOATING_BUTTON_SIZE_DEFAULT = 24;

    public static final String PATH = Environment.getExternalStorageDirectory() + "/TriviaHacK/";
    public static final String PATH_TO_ERRORS = Environment.getExternalStorageDirectory() + "/TriviaHacK/errors";
}
