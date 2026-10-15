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

package ai.loko.hk.ui.activities;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;

import ai.loko.hk.ui.constants.Constant;
import ui.R;

/**
 * Settings screen. All search engine and OCR related settings (and the tesseract
 * training data downloader) were removed together with that functionality. What is
 * left is the Logs/Debug screenshot saving switch and the floating button size slider.
 */
public class SettingsActivity extends AppCompatPreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupActionBar();
        getFragmentManager().beginTransaction().replace(android.R.id.content, new MainPreferenceFragment()).commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        finish();
    }

    private void setupActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show the Up button in the action bar.
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onIsMultiPane() {
        return isXLargeTablet(this);
    }

    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || MainPreferenceFragment.class.getName().equals(fragmentName);

    }

    @Override
    protected void onStop() {
        super.onStop();
        finish();
    }

    private static boolean isXLargeTablet(Context context) {
        return (context.getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class MainPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Store the default floating button size before the preferences are inflated
            // so the SeekBar of the "Floating button size" preference starts at the value
            // the floating button actually uses.
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(getActivity());
            if (!sharedPreferences.contains(Constant.FLOATING_BUTTON_SIZE)) {
                sharedPreferences.edit()
                        .putInt(Constant.FLOATING_BUTTON_SIZE, Constant.FLOATING_BUTTON_SIZE_DEFAULT)
                        .apply();
            }

            addPreferencesFromResource(R.xml.pref_main);
            setHasOptionsMenu(true);
            bindFloatingButtonSizeSummary(findPreference(Constant.FLOATING_BUTTON_SIZE));
        }

        private void bindFloatingButtonSizeSummary(Preference preference) {
            if (preference == null) return;
            final SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(preference.getContext());
            preference.setSummary(sizeSummary(sharedPreferences.getInt(
                    Constant.FLOATING_BUTTON_SIZE, Constant.FLOATING_BUTTON_SIZE_DEFAULT)));
            preference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary(sizeSummary((Integer) newValue));
                    return true;
                }
            });
        }

        private String sizeSummary(int storedValue) {
            if (storedValue < 0) storedValue = 0;
            if (storedValue > 100) storedValue = 100;
            return (Constant.FLOATING_BUTTON_SIZE_MIN + storedValue) + " dp";
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), ai.loko.hk.ui.MainActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }


}
