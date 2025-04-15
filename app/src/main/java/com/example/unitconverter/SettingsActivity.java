package com.example.unitconverter;

import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {
    
    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before creating the activity
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        updateTheme(isDarkMode);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Set up the theme switch
        SwitchMaterial themeSwitch = findViewById(R.id.themeSwitch);
        themeSwitch.setChecked(isDarkMode);
        
        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Save preference
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            
            // Update theme
            updateTheme(isChecked);
            
            // Recreate activity for theme to take effect
            recreate();
        });
    }

    private void updateTheme(boolean isDarkMode) {
        AppCompatDelegate.setDefaultNightMode(
            isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }
} 