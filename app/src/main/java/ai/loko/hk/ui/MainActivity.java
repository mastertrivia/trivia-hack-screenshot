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

package ai.loko.hk.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;

import com.github.javiersantos.appupdater.AppUpdater;
import com.github.javiersantos.appupdater.enums.UpdateFrom;
import com.nightonke.boommenu.BoomButtons.ButtonPlaceEnum;
import com.nightonke.boommenu.BoomButtons.HamButton;
import com.nightonke.boommenu.BoomButtons.OnBMClickListener;
import com.nightonke.boommenu.BoomMenuButton;
import com.nightonke.boommenu.ButtonEnum;
import com.nightonke.boommenu.Piece.PiecePlaceEnum;

import java.io.File;
import java.io.IOException;

import ai.loko.hk.ui.activities.ProfileActivity;
import ai.loko.hk.ui.activities.SettingsActivity;
import ai.loko.hk.ui.constants.Constant;
import ai.loko.hk.ui.ocr.MediaProjectionHelper;
import ai.loko.hk.ui.ocr.Points;
import ai.loko.hk.ui.services.Floating;
import ai.loko.hk.ui.utils.Logger;
import cn.pedant.SweetAlert.SweetAlertDialog;
import ui.BuildConfig;
import ui.R;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_STORAGE_PERMISSION = 153;
    private Intent mFloatingIntent;
    private TextView mOverlayPermmissionBtn, mAccessibilityPermissionBtn;
    private View mStartScreenshotBtn, mMyNotebooksBtn, mSettingsBtn, mSelectProfilesBtn;
    private MediaProjectionManager mMediaProjectionManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.new_launcher);
        setupActionBar();

        mFloatingIntent = new Intent(MainActivity.this, Floating.class);
        takeStoragePermission();
        checkForUpdates();

        mOverlayPermmissionBtn = findViewById(R.id.olpermission5);
        mAccessibilityPermissionBtn = findViewById(R.id.accpermission5);
        mStartScreenshotBtn = findViewById(R.id.start_screenshot);
        mMyNotebooksBtn = findViewById(R.id.my_notebooks);
        mSettingsBtn = findViewById(R.id.settings_card);
        mSelectProfilesBtn = findViewById(R.id.select_profiles);

        // START SCREENSHOT: starts the screenshot overlay with a full screen capture
        // profile, no profile has to be selected first.
        mStartScreenshotBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startScreenshot();
            }
        });

        // Not implemented yet.
        mMyNotebooksBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
            }
        });

        // Not implemented yet.
        mSettingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, R.string.coming_soon, Toast.LENGTH_SHORT).show();
            }
        });

        // Opens the existing profile selection screen.
        mSelectProfilesBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, ProfileActivity.class));
            }
        });

        givePermission();


    }

    private void checkForUpdates() {
        new AppUpdater(this)
                .setUpdateFrom(UpdateFrom.JSON)
                .setUpdateJSON("https://raw.githubusercontent.com/SubhamTyagi/loco-answers/master/update-changelog.json")
                .start();
    }

    private void takeStoragePermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.SYSTEM_ALERT_WINDOW,
                        Manifest.permission.INTERNET,
                        Manifest.permission.GET_TASKS
                },
                REQUEST_CODE_STORAGE_PERMISSION);
    }

    private void givePermission() {
        mOverlayPermmissionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                giveOverlayPermission();
            }
        });

        mAccessibilityPermissionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                giveAccessibilityPermission();
            }
        });
    }

    /**
     * Starts the screenshot capture workflow with a full screen profile: the complete
     * screen is captured and the user does not have to select a profile first.
     */
    private void startScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Toast.makeText(this, "Screenshots need Android 5.0 or later", Toast.LENGTH_LONG).show();
            return;
        }
        if (!canOverdraw()) {
            giveOverlayPermission();
            return;
        }

        // Internal full screen profile: coordinates covering the complete screen.
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        Points.X1 = 0;
        Points.Y1 = 0;
        Points.X2 = size.x;
        Points.Y2 = size.y;

        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(),
                Constant.CODE_FOR_SCREEN_CAPTURE);
    }

    private void setupActionBar() {
        ActionBar mActionBar = getSupportActionBar();
        if (mActionBar == null) throw new AssertionError();
        mActionBar.setDisplayShowHomeEnabled(false);
        mActionBar.setDisplayShowTitleEnabled(false);
        LayoutInflater mInflater = LayoutInflater.from(this);

        View actionBar = mInflater.inflate(R.layout.actionbar, null);
        TextView mTitleTextView = actionBar.findViewById(R.id.title_text);
        mTitleTextView.setText(R.string.app_name);
        mActionBar.setCustomView(actionBar);
        mActionBar.setDisplayShowCustomEnabled(true);
        ((Toolbar) actionBar.getParent()).setContentInsetsAbsolute(0, 0);

        BoomMenuButton rightBmb = actionBar.findViewById(R.id.action_bar_right_bmb);

        rightBmb.setButtonEnum(ButtonEnum.Ham);
        rightBmb.setPiecePlaceEnum(PiecePlaceEnum.HAM_4);
        rightBmb.setButtonPlaceEnum(ButtonPlaceEnum.HAM_4);

        rightBmb.addBuilder(new HamButton.Builder().normalImageRes(R.drawable.ic_settings_black_24dp).subNormalText("Floating button size, screenshot saving and other app options").normalText("Settings").listener(new OnBMClickListener() {
            @Override
            public void onBoomButtonClick(int index) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        }));

        rightBmb.addBuilder(new HamButton.Builder().normalImageRes(R.drawable.ic_help_white_24dp).subNormalText("If you are getting any error than get online help").normalText("Help Me").listener(new OnBMClickListener() {
            @Override
            public void onBoomButtonClick(int index) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SubhamTyagi/loco-answers/HELP.md")));
            }
        }));
        rightBmb.addBuilder(new HamButton.Builder().normalImageRes(R.drawable.ic_info_white_24dp).normalText("About").subNormalText("About me").listener(new OnBMClickListener() {
            @Override
            public void onBoomButtonClick(int index) {
                about();
            }
        }));
        rightBmb.addBuilder(new HamButton.Builder().normalImageRes(R.drawable.ic_directions_run_black_24dp).subNormalText("Click here to go to github release page").normalText("Update.").listener(new OnBMClickListener() {
            @Override
            public void onBoomButtonClick(int index) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/SubhamTyagi/loco-answers/releases/")));
            }
        }));
    }

    public boolean isAccessibilityEnabled() {
        int accessibilityEnabled = 0;
        final String ACCESSIBILITY_SERVICE = "ai.loko.hk.ui/ai.loko.hk.ui.Accessibility";
        try {
            accessibilityEnabled = Settings.Secure.getInt(this.getContentResolver(), android.provider.Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            //setting not found so your phone is not supported
        }
        TextUtils.SimpleStringSplitter mStringColonSplitter = new TextUtils.SimpleStringSplitter(':');
        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue);
                while (mStringColonSplitter.hasNext()) {
                    String accessabilityService = mStringColonSplitter.next();
                    if (accessabilityService.equalsIgnoreCase(ACCESSIBILITY_SERVICE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void giveAccessibilityPermission() {
        startActivity(new Intent("android.settings.ACCESSIBILITY_SETTINGS"));

    }

    private boolean canOverdraw() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void giveOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, Constant.CODE_DRAW_OVER_OTHER_APP_PERMISSION);
        } else {
            mOverlayPermmissionBtn.setBackgroundResource(R.drawable.bg_pill_green);
            Toast.makeText(this, "You do not need it", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Constant.CODE_DRAW_OVER_OTHER_APP_PERMISSION:
                //Check if the permission is granted or not.
                if (resultCode == RESULT_OK) {
                    mOverlayPermmissionBtn.setBackgroundResource(R.drawable.bg_pill_green);
                }
                break;
            case Constant.CODE_FOR_SCREEN_CAPTURE:
                if (resultCode == RESULT_OK && data != null) {
                    MediaProjectionHelper.setMediaProjectionManager(mMediaProjectionManager);
                    MediaProjectionHelper.setScreenshotPermission(data);
                    // Restart so the overlay always uses the current (full screen) profile.
                    stopService(mFloatingIntent);
                    startService(mFloatingIntent);
                    finish();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        setButtonsColorsAndText();
    }

    private void setButtonsColorsAndText() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            mOverlayPermmissionBtn.setBackgroundResource(R.drawable.bg_pill_green);
        } else {
            mOverlayPermmissionBtn.setBackgroundResource(R.drawable.bg_pill_red);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            mOverlayPermmissionBtn.setBackgroundResource(R.drawable.bg_pill_green);
        }
        if (isAccessibilityEnabled()) {
            mAccessibilityPermissionBtn.setBackgroundResource(R.drawable.bg_pill_green);
        } else {
            mAccessibilityPermissionBtn.setBackgroundResource(R.drawable.bg_pill_red);
        }
    }

    private void about() {
        new SweetAlertDialog(this, SweetAlertDialog.NORMAL_TYPE)
                .setTitleText("Trivia Hack VERSION " + BuildConfig.VERSION_NAME)
                .setContentText("This app is free and open source app hosted on GitHub")
                .setConfirmText("Ok")
                .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sweetAlertDialog) {
                        sweetAlertDialog.dismissWithAnimation();
                    }
                }).show();


    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                new File(Constant.PATH).mkdirs();
                new File(Constant.PATH_TO_ERRORS).mkdirs();

                try {
                    new File(Constant.PATH, ".nomedia").createNewFile();
                } catch (IOException e) {
                    Logger.logException(e);
                }
            } else {
                finish();
            }
        }

    }
}
