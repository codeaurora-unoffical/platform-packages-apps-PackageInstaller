/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.packageinstaller.role.model;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.voice.VoiceInteractionService;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.packageinstaller.permission.utils.Utils;
import com.android.packageinstaller.role.utils.UserUtils;
import com.android.permissioncontroller.R;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Class for behavior of the assistant role.
 */
public class AssistantRoleBehavior implements RoleBehavior {

    private static final String LOG_TAG = AssistantRoleBehavior.class.getSimpleName();

    private static final Intent ASSIST_SERVICE_PROBE =
            new Intent(VoiceInteractionService.SERVICE_INTERFACE);
    private static final Intent ASSIST_ACTIVITY_PROBE = new Intent(Intent.ACTION_ASSIST);

    @Override
    public boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return !UserUtils.isWorkProfile(user, context)
                && !context.getSystemService(ActivityManager.class).isLowRamDevice();
    }

    @Nullable
    @Override
    public String getFallbackHolder(@NonNull Role role, @NonNull Context context) {
        return ExclusiveDefaultHolderMixin.getDefaultHolder(role, "config_defaultAssistant",
                context);
    }

    @Override
    public boolean isVisibleAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return VisibilityMixin.isVisible("config_showDefaultAssistant", context);
    }

    @Nullable
    @Override
    public Intent getManageIntentAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return new Intent(Settings.ACTION_VOICE_INPUT_SETTINGS);
    }

    @Nullable
    @Override
    public CharSequence getConfirmationMessage(@NonNull Role role, @NonNull String packageName,
            @NonNull Context context) {
        return context.getString(R.string.assistant_confirmation_message);
    }

    @Nullable
    @Override
    public List<String> getQualifyingPackagesAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        Set<String> availableAssistants = new ArraySet<>();

        List<ResolveInfo> services = pm.queryIntentServicesAsUser(ASSIST_SERVICE_PROBE,
                PackageManager.GET_META_DATA, user);

        int numServices = services.size();
        for (int i = 0; i < numServices; i++) {
            ResolveInfo service = services.get(i);

            if (isAssistantVoiceInteractionService(pm, service.serviceInfo)) {
                availableAssistants.add(service.serviceInfo.packageName);
            }
        }

        List<ResolveInfo> activities = pm.queryIntentActivitiesAsUser(ASSIST_ACTIVITY_PROBE,
                PackageManager.MATCH_DEFAULT_ONLY, user);

        int numActivities = activities.size();
        for (int i = 0; i < numActivities; i++) {
            availableAssistants.add(activities.get(i).activityInfo.packageName);
        }

        return new ArrayList<>(availableAssistants);
    }

    @Nullable
    @Override
    public Boolean isPackageQualified(@NonNull Role role, @NonNull String packageName,
            @NonNull Context context) {
        PackageManager pm = context.getPackageManager();

        Intent pkgServiceProbe = new Intent(ASSIST_SERVICE_PROBE).setPackage(packageName);
        List<ResolveInfo> services = pm.queryIntentServices(pkgServiceProbe,
                PackageManager.GET_META_DATA);

        int numServices = services.size();
        for (int i = 0; i < numServices; i++) {
            ResolveInfo service = services.get(i);

            if (isAssistantVoiceInteractionService(pm, service.serviceInfo)) {
                return true;
            }
        }

        Intent pkgActivityProbe = new Intent(ASSIST_ACTIVITY_PROBE).setPackage(packageName);
        boolean hasAssistantActivity = !pm.queryIntentActivities(pkgActivityProbe,
                PackageManager.MATCH_DEFAULT_ONLY).isEmpty();

        if (!hasAssistantActivity) {
            Log.w(LOG_TAG, "Package " + packageName + " not qualified for " + role.getName()
                    + " due to " + (services.isEmpty() ? "missing service"
                    : "service without qualifying metadata") + " and missing activity");
        }

        return hasAssistantActivity;
    }

    private boolean isAssistantVoiceInteractionService(@NonNull PackageManager pm,
            @NonNull ServiceInfo si) {
        if (!android.Manifest.permission.BIND_VOICE_INTERACTION.equals(si.permission)) {
            return false;
        }

        try (XmlResourceParser parser = si.loadXmlMetaData(pm,
                VoiceInteractionService.SERVICE_META_DATA)) {
            if (parser == null) {
                return false;
            }

            int type;
            do {
                type = parser.next();
            } while (type != XmlResourceParser.END_DOCUMENT && type != XmlResourceParser.START_TAG);

            String sessionService = null;
            String recognitionService = null;
            boolean supportsAssist = false;

            AttributeSet attrs = Xml.asAttributeSet(parser);
            int numAttrs = attrs.getAttributeCount();
            for (int i = 0; i < numAttrs; i++) {
                switch (attrs.getAttributeNameResource(i)) {
                    case android.R.attr.sessionService:
                        sessionService = attrs.getAttributeValue(i);
                        break;
                    case android.R.attr.recognitionService:
                        recognitionService = attrs.getAttributeValue(i);
                        break;
                    case android.R.attr.supportsAssist:
                        supportsAssist = attrs.getAttributeBooleanValue(i, false);
                        break;
                }
            }

            if (sessionService == null || recognitionService == null || !supportsAssist) {
                return false;
            }
        } catch (XmlPullParserException | IOException | Resources.NotFoundException ignored) {
            return false;
        }

        return true;
    }

    @Override
    public void onHolderChangedAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        Utils.updateUserSensitive((Application) context.getApplicationContext(), user);
    }
}
