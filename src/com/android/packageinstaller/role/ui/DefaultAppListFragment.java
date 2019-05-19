/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.packageinstaller.role.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProviders;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.role.model.Role;
import com.android.packageinstaller.role.model.Roles;
import com.android.permissioncontroller.R;

import java.util.List;
import java.util.Objects;

/**
 * Fragment for the list of default apps.
 */
public class DefaultAppListFragment extends SettingsFragment
        implements Preference.OnPreferenceClickListener {

    private static final String LOG_TAG = DefaultAppListFragment.class.getSimpleName();

    private static final String PREFERENCE_KEY_MORE_DEFAULT_APPS =
            DefaultAppListFragment.class.getName() + ".preference.MORE_DEFAULT_APPS";

    private static final String PREFERENCE_KEY_MANAGE_DOMAIN_URLS =
            DefaultAppListFragment.class.getName() + ".preference.MANAGE_DOMAIN_URLS";

    private static final String PREFERENCE_KEY_WORK_CATEGORY =
            DefaultAppListFragment.class.getName() + ".preference.WORK_CATEGORY";

    private DefaultAppListViewModel mViewModel;

    /**
     * Create a new instance of this fragment.
     *
     * @return a new instance of this fragment
     */
    @NonNull
    public static DefaultAppListFragment newInstance() {
        return new DefaultAppListFragment();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mViewModel = ViewModelProviders.of(this).get(DefaultAppListViewModel.class);
        mViewModel.getLiveData().observe(this, roleItems -> onRoleListChanged());
        if (mViewModel.hasWorkProfile()) {
            mViewModel.getWorkLiveData().observe(this, roleItems -> onRoleListChanged());
        }
    }

    @Override
    @StringRes
    protected int getEmptyTextResource() {
        return R.string.no_default_apps;
    }

    @Override
    protected int getHelpUriResource() {
        return R.string.help_uri_default_apps;
    }

    private void onRoleListChanged() {
        List<RoleItem> roleItems = mViewModel.getLiveData().getValue();
        if (roleItems == null) {
            return;
        }
        boolean hasWorkProfile = mViewModel.hasWorkProfile();
        List<RoleItem> workRoleItems = null;
        if (hasWorkProfile) {
            workRoleItems = mViewModel.getWorkLiveData().getValue();
            if (workRoleItems == null) {
                return;
            }
        }

        PreferenceManager preferenceManager = getPreferenceManager();
        Context context = preferenceManager.getContext();
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        ArrayMap<String, Preference> oldPreferences = new ArrayMap<>();
        PreferenceCategory oldWorkPreferenceCategory = null;
        ArrayMap<String, Preference> oldWorkPreferences = new ArrayMap<>();
        if (preferenceScreen == null) {
            preferenceScreen = preferenceManager.createPreferenceScreen(context);
            setPreferenceScreen(preferenceScreen);
        } else {
            oldWorkPreferenceCategory = (PreferenceCategory) preferenceScreen.findPreference(
                    PREFERENCE_KEY_WORK_CATEGORY);
            if (oldWorkPreferenceCategory != null) {
                clearPreferences(oldWorkPreferenceCategory, oldWorkPreferences);
                preferenceScreen.removePreference(oldWorkPreferenceCategory);
            }
            clearPreferences(preferenceScreen, oldPreferences);
        }

        addPreferences(preferenceScreen, roleItems, oldPreferences, this, mViewModel.getUser(),
                context);
        addMoreDefaultAppsPreference(preferenceScreen, oldPreferences, context);
        addManageDomainUrlsPreference(preferenceScreen, oldPreferences, context);
        if (hasWorkProfile && !workRoleItems.isEmpty()) {
            PreferenceCategory workPreferenceCategory = oldWorkPreferenceCategory;
            if (workPreferenceCategory == null) {
                workPreferenceCategory = new PreferenceCategory(context);
                workPreferenceCategory.setKey(PREFERENCE_KEY_WORK_CATEGORY);
                workPreferenceCategory.setTitle(R.string.default_apps_for_work);
            }
            preferenceScreen.addPreference(workPreferenceCategory);
            addPreferences(workPreferenceCategory, workRoleItems, oldWorkPreferences, this,
                    mViewModel.getWorkProfile(), context);
        }

        updateState();
    }

    private static void clearPreferences(@NonNull PreferenceGroup preferenceGroup,
            @NonNull ArrayMap<String, Preference> oldPreferences) {
        for (int i = preferenceGroup.getPreferenceCount() - 1; i >= 0; --i) {
            Preference Preference = preferenceGroup.getPreference(i);

            oldPreferences.put(Preference.getKey(), Preference);
        }
    }

    private static void addPreferences(@NonNull PreferenceGroup preferenceGroup,
            @NonNull List<RoleItem> roleItems, @NonNull ArrayMap<String, Preference> oldPreferences,
            @NonNull Preference.OnPreferenceClickListener listener, @NonNull UserHandle user,
            @NonNull Context context) {
        int roleItemsSize = roleItems.size();
        for (int i = 0; i < roleItemsSize; i++) {
            RoleItem roleItem = roleItems.get(i);

            Role role = roleItem.getRole();
            AppIconSettingsButtonPreference preference =
                    (AppIconSettingsButtonPreference) oldPreferences.get(role.getName());
            if (preference == null) {
                preference = new AppIconSettingsButtonPreference(context);
                preference.setKey(role.getName());
                preference.setIconSpaceReserved(true);
                preference.setTitle(role.getShortLabelResource());
                preference.setPersistent(false);
                preference.setOnPreferenceClickListener(listener);
                preference.getExtras().putParcelable(Intent.EXTRA_USER, user);
            }

            List<ApplicationInfo> holderApplicationInfos = roleItem.getHolderApplicationInfos();
            if (holderApplicationInfos.isEmpty()) {
                preference.setIcon(null);
                preference.setSummary(R.string.default_app_none);
            } else {
                ApplicationInfo holderApplicationInfo = holderApplicationInfos.get(0);
                preference.setIcon(Utils.getBadgedIcon(context, holderApplicationInfo));
                preference.setSummary(Utils.getAppLabel(holderApplicationInfo, context));
            }
            role.preparePreferenceAsUser(preference, user, context);

            preferenceGroup.addPreference(preference);
        }
    }

    @Override
    public boolean onPreferenceClick(@NonNull Preference preference) {
        String roleName = preference.getKey();
        Context context = requireContext();
        Role role = Roles.get(context).get(roleName);
        UserHandle user = preference.getExtras().getParcelable(Intent.EXTRA_USER);
        Intent intent = role.getManageIntentAsUser(user, context);
        if (intent == null) {
            intent = DefaultAppActivity.createIntent(roleName, user, requireContext());
        }
        startActivity(intent);
        return true;
    }

    private static void addMoreDefaultAppsPreference(@NonNull PreferenceGroup preferenceGroup,
            @NonNull ArrayMap<String, Preference> oldPreferences, @NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_MORE_DEFAULT_APPS_SETTINGS);
        if (!isIntentResolvedToSettings(intent, context)) {
            return;
        }

        Preference preference = oldPreferences.get(PREFERENCE_KEY_MORE_DEFAULT_APPS);
        if (preference == null) {
            preference = new Preference(context);
            preference.setKey(PREFERENCE_KEY_MORE_DEFAULT_APPS);
            preference.setIconSpaceReserved(true);
            preference.setTitle(context.getString(R.string.default_apps_more));
            preference.setPersistent(false);
            preference.setOnPreferenceClickListener(preference2 -> {
                context.startActivity(intent);
                return true;
            });
        }

        preferenceGroup.addPreference(preference);
    }

    private static void addManageDomainUrlsPreference(@NonNull PreferenceGroup preferenceGroup,
            @NonNull ArrayMap<String, Preference> oldPreferences, @NonNull Context context) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_DOMAIN_URLS);
        if (!isIntentResolvedToSettings(intent, context)) {
            return;
        }

        Preference preference = oldPreferences.get(PREFERENCE_KEY_MANAGE_DOMAIN_URLS);
        if (preference == null) {
            preference = new Preference(context);
            preference.setKey(PREFERENCE_KEY_MANAGE_DOMAIN_URLS);
            preference.setIconSpaceReserved(true);
            preference.setTitle(context.getString(R.string.default_apps_manage_domain_urls));
            preference.setPersistent(false);
            preference.setOnPreferenceClickListener(preference2 -> {
                context.startActivity(intent);
                return true;
            });
        }

        preferenceGroup.addPreference(preference);
    }

    private static boolean isIntentResolvedToSettings(@NonNull Intent intent,
            @NonNull Context context) {
        PackageManager packageManager = context.getPackageManager();
        ComponentName componentName = intent.resolveActivity(packageManager);
        if (componentName == null) {
            return false;
        }
        Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
        String settingsPackageName = settingsIntent.resolveActivity(packageManager)
                .getPackageName();
        return Objects.equals(componentName.getPackageName(), settingsPackageName);
    }
}
