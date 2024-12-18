/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.carlauncher;

import static com.android.car.carlauncher.AppGridFragment.MODE_INTENT_EXTRA;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.android.car.carlauncher.AppGridFragment.Mode;
import com.android.car.ui.core.CarUi;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.NavButtonMode;
import com.android.car.ui.toolbar.ToolbarController;

import java.util.Collections;

/**
 * Launcher activity that shows a grid of apps.
 */
public class AppGridActivity extends AppCompatActivity {
    private static final String TAG = "AppGridActivity";
    boolean mShowToolbar = false;
    boolean mShowAllApps = true;
    private static final boolean DEBUG_BUILD = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // TODO (b/267548246) deprecate toolbar and find another way to hide debug apps
        if (mShowToolbar) {
            setTheme(R.style.Theme_Launcher_AppGridActivity);
        } else {
            setTheme(R.style.Theme_Launcher_AppGridActivity_NoToolbar);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_grid_container_activity);

        if (mShowToolbar) {
            ToolbarController toolbar = CarUi.requireToolbar(this);
            toolbar.setNavButtonMode(NavButtonMode.CLOSE);
            if (DEBUG_BUILD) {
                toolbar.setMenuItems(Collections.singletonList(MenuItem.builder(this)
                        .setDisplayBehavior(MenuItem.DisplayBehavior.NEVER)
                        .setTitle(R.string.hide_debug_apps)
                        .setOnClickListener(i -> {
                            mShowAllApps = !mShowAllApps;
                            i.setTitle(mShowAllApps
                                    ? R.string.hide_debug_apps
                                    : R.string.show_debug_apps);
                        })
                        .build()));
            }
        }
        getSupportFragmentManager().beginTransaction().replace(R.id.fragmentContainer,
                AppGridFragment.newInstance(parseMode(getIntent()))).commit();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Mode mode = parseMode(intent);
        setTitle(mode.getTitleStringId());
        if (mShowToolbar) {
            CarUi.requireToolbar(this).setTitle(mode.getTitleStringId());
        }
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (fragment instanceof AppGridFragment) {
            ((AppGridFragment) fragment).updateMode(mode);
        }
    }

    /**
     * Note: This activity is exported, meaning that it might receive intents from any source.
     * Intent data parsing must be extra careful.
     */
    @NonNull
    private Mode parseMode(@Nullable Intent intent) {
        String mode = intent != null ? intent.getStringExtra(MODE_INTENT_EXTRA) : null;
        try {
            return mode != null ? Mode.valueOf(mode) : Mode.ALL_APPS;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Received invalid mode: " + mode, e);
        }
    }

}
