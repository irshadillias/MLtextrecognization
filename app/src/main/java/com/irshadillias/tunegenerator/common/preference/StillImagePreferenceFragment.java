package com.irshadillias.tunegenerator.common.preference;

import android.os.Bundle;
import android.preference.PreferenceFragment;

import com.irshadillias.tunegenerator.R;


/** Configures still image demo settings. */
public class StillImagePreferenceFragment extends PreferenceFragment {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    addPreferencesFromResource(R.xml.preference_still_image);
  }
}
