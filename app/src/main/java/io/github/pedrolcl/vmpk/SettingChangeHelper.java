/* SPDX-License-Identifier: GPL-3.0-or-later */
/* Copyright © 2013–2026 Pedro López-Cabanillas. */

package io.github.pedrolcl.vmpk;

import static android.content.Context.MIDI_SERVICE;
import static android.content.pm.PackageManager.FEATURE_MIDI;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.preference.PreferenceManager;

import java.util.Locale;

public class SettingChangeHelper {
	public static final int MIDI_OUTPUT_MODE_SYSTEM = 0;
	public static final int MIDI_OUTPUT_MODE_NETWORK = 1;
	public static final int MIDI_OUTPUT_MODE_INTERNAL_SYNTH = 2;

	private static boolean mLastTheme = false;
	private static int mLastOutput = MIDI_OUTPUT_MODE_SYSTEM;
	private static String mLastLang = null;
	// private static boolean mFullScreen = false;

	public static void changeSettingsCheck(Activity activity) {
		boolean newTheme = getCurrentTheme(activity);
		int newOutput = getCurrentOutputMode(activity);
		String newLang = getCurrentLanguage(activity);
		if (newTheme != mLastTheme || newOutput != mLastOutput || newLang != mLastLang) {
			Log.d("SettingChangeHelper", "changingSettings");
			// activity.recreate(); NO USAR, PODRIDO!
			activity.finish();
			activity.startActivity(new Intent(activity, activity.getClass()));
		}
	}

	public static void onMainActivityCreateApplySettings(Activity activity) {
		mLastTheme = getCurrentTheme(activity);
		mLastOutput = getCurrentOutputMode(activity);
		mLastLang = getCurrentLanguage(activity);
		Log.d("SettingChangeHelper", "onActivityCreateApplySettings");
		if (mLastTheme) {
			activity.setTheme(R.style.LightTheme);
		} else {
			activity.setTheme(R.style.DarkTheme);
		}
		Configuration config = new Configuration();
		Locale newLocale = new Locale(mLastLang);
		config.locale = newLocale;
		Locale.setDefault(newLocale);
		activity.getResources().updateConfiguration(config, null);
	}

	public static void onActivityCreateApplyTheme(Activity activity) {
		boolean theme = getCurrentTheme(activity);
		if (theme) {
			activity.setTheme(R.style.LightTheme);
		} else {
			activity.setTheme(R.style.DarkTheme);
		}
		Configuration config = new Configuration();
		config.locale = new Locale(getCurrentLanguage(activity));
		activity.getResources().updateConfiguration(config, null);
	}

	private static boolean getCurrentTheme(Activity activity) {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
		return sharedPrefs.getBoolean("alternate_theme", false);
	}

	public static int getCurrentOutputMode(Activity activity) {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
		int output = -1;
		try {
			output = Integer.parseInt(sharedPrefs.getString("midi_output_mode", ""));
		} catch (NumberFormatException e) {
		} catch (ClassCastException e) {
        }
		if (output < MIDI_OUTPUT_MODE_SYSTEM || output > MIDI_OUTPUT_MODE_INTERNAL_SYNTH) {
			if (sharedPrefs.contains("midi_output")) {
				output = sharedPrefs.getBoolean("midi_output", true)
						? MIDI_OUTPUT_MODE_INTERNAL_SYNTH
						: MIDI_OUTPUT_MODE_NETWORK;
			} else {
				output = MIDI_OUTPUT_MODE_INTERNAL_SYNTH;
			}
			sharedPrefs.edit()
					.putString("midi_output_mode", Integer.toString(output))
					.remove("midi_output")
					.commit();
		}
		if (output == MIDI_OUTPUT_MODE_SYSTEM) {
			if (!activity.getPackageManager().hasSystemFeature(FEATURE_MIDI)
					|| activity.getSystemService(MIDI_SERVICE) == null) {
				output = MIDI_OUTPUT_MODE_INTERNAL_SYNTH;
			}
		}
		return output;
	}

	private static String getCurrentLanguage(Activity activity) {
		SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(activity);
		String defLang = activity.getResources().getString(R.string.default_language);
		if (mLastLang == null) {
			mLastLang = defLang;
		}
		return sharedPrefs.getString("lang", defLang);
	}

	// public static boolean getFullScreen(Activity activity) {
	// return mFullScreen;
	// }

}
