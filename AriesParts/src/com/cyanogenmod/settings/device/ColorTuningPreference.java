package com.cyanogenmod.settings.device;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.DialogPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Special preference type that allows configuration of both the ring volume and
 * notification volume.
 */
public class ColorTuningPreference extends DialogPreference {

    enum Colors {
        RED,
        GREEN,
        BLUE
    };

    private static final int[] SEEKBAR_ID = new int[] {
        R.id.color_red_seekbar,
        R.id.color_green_seekbar,
        R.id.color_blue_seekbar
    };

    private static final int[] VALUE_DISPLAY_ID = new int[] {
        R.id.color_red_value,
        R.id.color_green_value,
        R.id.color_blue_value
    };

    private static final int[] GAMMA_SEEKBAR_ID = new int[] {
        R.id.color_red_gamma_seekbar,
        R.id.color_green_gamma_seekbar,
        R.id.color_blue_gamma_seekbar,
    };

    private static final int[] GAMMA_VALUE_DISPLAY_ID = new int[] {
        R.id.color_red_gamma_value,
        R.id.color_green_gamma_value,
        R.id.color_blue_gamma_value
    };

    private static final String[] FILE_PATH = new String[] {
        "/sys/devices/virtual/misc/color_tuning/red_multiplier",
        "/sys/devices/virtual/misc/color_tuning/green_multiplier",
        "/sys/devices/virtual/misc/color_tuning/blue_multiplier"
    };

    private static final String[] GAMMA_FILE_PATH = new String[] {
        "/sys/devices/virtual/misc/color_tuning/red_v1_offset",
        "/sys/devices/virtual/misc/color_tuning/green_v1_offset",
        "/sys/devices/virtual/misc/color_tuning/blue_v1_offset"
    };

    private ColorSeekBar mSeekBars[] = new ColorSeekBar[6];

    private static final int MAX_VALUE = Integer.MAX_VALUE;

    private static final int GAMMA_MAX_VALUE = 20;
    private static final int GAMMA_DEFAULT_VALUE = 0;

    // Track instances to know when to restore original color
    // (when the orientation changes, a new dialog is created before the old one is destroyed)
    private static int sInstances = 0;

    public ColorTuningPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.preference_dialog_color_tuning);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        sInstances++;

        for (int i = 0; i < SEEKBAR_ID.length; i++) {
            SeekBar seekBar = (SeekBar) view.findViewById(SEEKBAR_ID[i]);
            TextView valueDisplay = (TextView) view.findViewById(VALUE_DISPLAY_ID[i]);
            mSeekBars[i] = new ColorSeekBar(seekBar, valueDisplay, FILE_PATH[i]);
        }

        for (int i = 0; i < GAMMA_SEEKBAR_ID.length; i++) {
            SeekBar seekBar = (SeekBar) view.findViewById(GAMMA_SEEKBAR_ID[i]);
            TextView valueDisplay = (TextView) view.findViewById(GAMMA_VALUE_DISPLAY_ID[i]);
            mSeekBars[SEEKBAR_ID.length + i] = new GammaSeekBar(seekBar, valueDisplay, GAMMA_FILE_PATH[i]);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        sInstances--;

        if (positiveResult) {
            for (ColorSeekBar csb : mSeekBars) {
                csb.save();
            }
        } else if (sInstances == 0) {
            for (ColorSeekBar csb : mSeekBars) {
                csb.reset();
            }
        }
    }

    /**
     * Restore screen color tuning from SharedPreferences. (Write to kernel.)
     * @param context       The context to read the SharedPreferences from
     */
    public static void restore(Context context) {
        if (!isSupported()) {
            return;
        }

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        for (String filePath : FILE_PATH) {
            int value = sharedPrefs.getInt(filePath, MAX_VALUE);
            Utils.writeColor(filePath, value);
        }
        for (String filePath : GAMMA_FILE_PATH) {
            int value = sharedPrefs.getInt(filePath, GAMMA_DEFAULT_VALUE);
            Utils.writeColor(filePath, value);
        }
    }

    /**
     * Check whether the running kernel supports color tuning or not.
     * @return              Whether color tuning is supported or not
     */
    public static boolean isSupported() {
        boolean supported = true;
        for (String filePath : FILE_PATH) {
            if (!Utils.fileExists(filePath)) {
                supported = false;
            }
        }
        for (String filePath : GAMMA_FILE_PATH) {
            if (!Utils.fileExists(filePath)) {
                supported = false;
            }
        }

        return supported;
    }

    class ColorSeekBar implements SeekBar.OnSeekBarChangeListener {

        protected String mFilePath;
        protected int mOriginal;
        protected SeekBar mSeekBar;
        protected TextView mValueDisplay;

        public ColorSeekBar(SeekBar seekBar, TextView valueDisplay, String filePath) {
            mSeekBar = seekBar;
            mValueDisplay = valueDisplay;
            mFilePath = filePath;

            // Read original value
            SharedPreferences sharedPreferences = getSharedPreferences();
            mOriginal = sharedPreferences.getInt(mFilePath, MAX_VALUE);

            seekBar.setMax(MAX_VALUE);
            reset();
            seekBar.setOnSeekBarChangeListener(this);
        }

        // For inheriting class
        protected ColorSeekBar() {
        }

        public void reset() {
            mSeekBar.setProgress(mOriginal);
            updateValue(mOriginal);
        }

        public void save() {
            Editor editor = getEditor();
            editor.putInt(mFilePath, mSeekBar.getProgress());
            editor.commit();
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress,
                boolean fromUser) {
            Utils.writeColor(mFilePath, progress);
            updateValue(progress);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // Do nothing
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // Do nothing
        }

        protected void updateValue(int progress) {
            mValueDisplay.setText(String.format("%.3f", (double) progress / MAX_VALUE));
        }

    }

    class GammaSeekBar extends ColorSeekBar {

        public GammaSeekBar(SeekBar seekBar, TextView valueDisplay, String filePath) {
            mSeekBar = seekBar;
            mValueDisplay = valueDisplay;
            mFilePath = filePath;

            // Read original value
            SharedPreferences sharedPreferences = getSharedPreferences();
            mOriginal = sharedPreferences.getInt(mFilePath, GAMMA_DEFAULT_VALUE);

            seekBar.setMax(GAMMA_MAX_VALUE);
            reset();
            seekBar.setOnSeekBarChangeListener(this);
        }

        @Override
        protected void updateValue(int progress) {
            mValueDisplay.setText("-" + progress);
        }

    }
}
