/*
 * Copyright (C) 2014 Murray Cumming
 *
 * This file is part of android-galaxyzoo.
 *
 * android-galaxyzoo is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * android-galaxyzoo is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with android-galaxyzoo.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.murrayc.galaxyzoo.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * A singleton that allows our various Activities to share the same data.
 * <p/>
 * This feels hacky, but it a recommended way for Activities to share non-primitive data:
 * http://developer.android.com/guide/faq/framework.html#3
 */
public class Singleton {

    private static final String JSON_FILE_EXTENSION = ".json";
    private static Callbacks mCallbacks;
    private static Singleton ourInstance = null;
    private IconsCache mIconsCache = null;
    private DecisionTree mDecisionTree = null;
    private LocaleDetails mLocaleDetails = null;

    private Singleton(final Context context) {
        //This needs to be done as soon as the app opens.
        //See http://developer.android.com/guide/topics/ui/settings.html#Fragment
        PreferenceManager.setDefaultValues(context, R.xml.preferences, false);


        //Try to find a translation file:
        InputStream inputStreamTranslation = null;
        mLocaleDetails = getLocaleDetails(context);
        if (!TextUtils.isEmpty(mLocaleDetails.language)) {
            //Try finding a translation for a country-specific form of the language:
            String translationFileName = mLocaleDetails.language + "_" + mLocaleDetails.countryCode + JSON_FILE_EXTENSION;
            inputStreamTranslation = openAsset(context, translationFileName);
            if (inputStreamTranslation == null) {
                //Try just the language instead:
                translationFileName = mLocaleDetails.language + JSON_FILE_EXTENSION;
                inputStreamTranslation = openAsset(context, translationFileName);
            }
        }

        final InputStream inputStreamTree = openAsset(context, "sloan_tree.xml");
        if (inputStreamTree == null) {
            Log.error("Singleton: Error parsing decision tree.");
        } else {
            mDecisionTree = new DecisionTree(inputStreamTree, inputStreamTranslation);
        }

        if (inputStreamTree != null) {
            try {
                inputStreamTree.close();
            } catch (final IOException e) {
                Log.error("Singleton: Exception while closing inputStreamTree", e);
            }
        }

        if (inputStreamTranslation != null) {
            try {
                inputStreamTranslation.close();
            } catch (final IOException e) {
                Log.error("Singleton: Exception while closing inputStreamTranslation", e);
            }
        }

        mIconsCache = new IconsCache(context, mDecisionTree);
    }

    private static LocaleDetails getLocaleDetails(final Context context) {
        final Locale locale = context.getResources().getConfiguration().locale;
        final LocaleDetails result = new LocaleDetails();

        result.language = locale.getLanguage();

        //The Galaxy zoo files, such as ch_cn.json are lowercase, instead of having the
        //country code in uppercase, such as ch_CN, like normal system locales.
        result.countryCode =  locale.getCountry().toLowerCase();

        return result;
    }

    private InputStream openAsset(Context context, String translationFileName) {
        try {
            return context.getAssets().open(translationFileName);
        } catch (final IOException e) {
            //Don't log this because we expect the file to not exist sometimes,
            //and the caller will ust check for a null result to know that.
        }

        return null;
    }

    private static void onLoginTaskFinished() {
        if (mCallbacks != null) {
            mCallbacks.onInitialized();
        }
    }

    public static void init(final Context context, final Callbacks callbacks) {
        //Just notify the caller if it has already been initialized.
        if (ourInstance != null) {

            //Check that the context's language is the same:
            if(!ourInstance.localeIsDifferent(context)) {
                //Just use the existing instance:
                callbacks.onInitialized();
                return;
            }
        }

        // Instantiate the Singleton and call our callback later:
        mCallbacks = callbacks;
        final InitAsyncTask task = new InitAsyncTask();
        task.execute(context);
    }

    public static Singleton getInstance() {
        return ourInstance;
    }

    public DecisionTree getDecisionTree() {
        return mDecisionTree;
    }

    private Bitmap getIcon(final String iconName) {
        return mIconsCache.getIcon(iconName);
    }

    public BitmapDrawable getIconDrawable(final Context context, final String iconName) {
        final Bitmap bitmap = getIcon(iconName);
        if (bitmap == null) {
            return null;
        }

        final BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        drawable.setBounds(0, 0, 100, 100); //TODO: Avoid hardcoding.
        return drawable;
    }

    public BitmapDrawable getIconDrawable(final Context context, final DecisionTree.BaseButton answer) {
        return getIconDrawable(context, answer.getIcon());
    }

    /**
     * Find out whether the context's locale is different enough to the one used when
     * this instance was created.
     */
    public boolean localeIsDifferent(final Context context) {
        //This doesn't care about whether there is really a country-specific
        //translation available. So this will sometimes lead to
        //an unnecessary reload if the country (but not the language)
        //changes, but that is rare enough not to be a problem.
        final LocaleDetails newDetails = getLocaleDetails(context);
        return !newDetails.equals(mLocaleDetails);
    }

    public static interface Callbacks {
        /**
         * This is called when the Singleton has been initialized.
         * This will be called immediately if the Singleton has
         * already been initialized.
         * When this has been called, getInstance() will return
         * a non-null value.
         */
        public void onInitialized();
    }

    private static class InitAsyncTask extends AsyncTask<Context, Void, Void> {
        @Override
        protected Void doInBackground(final Context... params) {
            if (params.length < 1) {
                Log.error("InitAsyncTask: not enough params.");
                return null;
            }

            final Context context = params[0];
            Singleton.ourInstance = new Singleton(context);

            return null;
        }

        @Override
        protected void onPostExecute(final Void result) {
            super.onPostExecute(result);

            onLoginTaskFinished();
        }
    }

    private static class LocaleDetails {
        public String language;
        public String countryCode;

        @Override
        public boolean equals(Object o) {
            //This code was generated by Android Studio:
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final LocaleDetails that = (LocaleDetails) o;

            if (countryCode != null ? !countryCode.equals(that.countryCode) : that.countryCode != null)
                return false;
            if (language != null ? !language.equals(that.language) : that.language != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = language != null ? language.hashCode() : 0;
            result = 31 * result + (countryCode != null ? countryCode.hashCode() : 0);
            return result;
        }
    }
}
