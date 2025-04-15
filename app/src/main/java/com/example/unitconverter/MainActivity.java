package com.example.unitconverter;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import java.util.HashMap;
import java.util.Map;
import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity {

    private EditText inputValue1;
    private TextView inputValue2;
    private AutoCompleteTextView unit1, unit2;
    private TextView unitDisplay1, unitDisplay2;

    private static final String PREFS_NAME = "theme_prefs";
    private static final String KEY_DARK_MODE = "dark_mode";

    // Conversion rates for length units (base unit: meters)
    private final Map<String, Double> conversionRates = new HashMap<>();
    private final Map<String, String> unitSymbols = new HashMap<>();
    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.##");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme before creating the activity
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        updateTheme(isDarkMode);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupConversionRates();
        setupUnitSelectors();
        setupInputListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Check if theme changed
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean isDarkMode = prefs.getBoolean(KEY_DARK_MODE, false);
        int currentNightMode = AppCompatDelegate.getDefaultNightMode();
        boolean isCurrentlyDark = currentNightMode == AppCompatDelegate.MODE_NIGHT_YES;
        
        if (isDarkMode != isCurrentlyDark) {
            updateTheme(isDarkMode);
            recreate();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateTheme(boolean isDarkMode) {
        AppCompatDelegate.setDefaultNightMode(
            isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
    }

    private void initializeViews() {
        inputValue1 = findViewById(R.id.inputValue1);
        inputValue2 = findViewById(R.id.inputValue2);
        unit1 = findViewById(R.id.unit1);
        unit2 = findViewById(R.id.unit2);
        unitDisplay1 = findViewById(R.id.unitDisplay1);
        unitDisplay2 = findViewById(R.id.unitDisplay2);
    }

    private void setupConversionRates() {
        // Length conversions (base unit: meters)
        conversionRates.put(getString(R.string.meters), 1.0);
        conversionRates.put(getString(R.string.centimeters), 100.0);
        conversionRates.put(getString(R.string.feet), 3.28084);
        conversionRates.put(getString(R.string.inches), 39.3701);
        conversionRates.put(getString(R.string.yards), 1.09361);

        // Unit symbols
        unitSymbols.put(getString(R.string.meters), getString(R.string.meters_symbol));
        unitSymbols.put(getString(R.string.centimeters), getString(R.string.centimeters_symbol));
        unitSymbols.put(getString(R.string.feet), getString(R.string.feet_symbol));
        unitSymbols.put(getString(R.string.inches), getString(R.string.inches_symbol));
        unitSymbols.put(getString(R.string.yards), getString(R.string.yards_symbol));
    }

    private void setupUnitSelectors() {
        String[] units = conversionRates.keySet().toArray(new String[0]);
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                units
        );

        unit1.setAdapter(unitAdapter);
        unit2.setAdapter(unitAdapter);

        // Set default units
        String defaultFromUnit = getString(R.string.meters);
        String defaultToUnit = getString(R.string.centimeters);
        
        unit1.setText(defaultFromUnit, false);
        unit2.setText(defaultToUnit, false);
        unitDisplay1.setText(unitSymbols.get(defaultFromUnit));
        unitDisplay2.setText(unitSymbols.get(defaultToUnit));

        // Add listeners for unit changes
        unit1.setOnItemClickListener((parent, view, position, id) -> {
            String selectedUnit = units[position];
            String symbol = unitSymbols.get(selectedUnit);
            if (symbol != null) {
                unitDisplay1.setText(symbol);
            }
            convert();
        });
        
        unit2.setOnItemClickListener((parent, view, position, id) -> {
            String selectedUnit = units[position];
            String symbol = unitSymbols.get(selectedUnit);
            if (symbol != null) {
                unitDisplay2.setText(symbol);
            }
            convert();
        });
    }

    private void setupInputListeners() {
        inputValue1.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Remove any non-numeric characters except decimal point and minus
                String filtered = s.toString().replaceAll("[^\\d.-]", "");
                if (!filtered.equals(s.toString())) {
                    inputValue1.setText(filtered);
                    inputValue1.setSelection(filtered.length());
                    return;
                }
                convert();
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Handle multiple decimal points
                String text = s.toString();
                int decimalPoints = text.length() - text.replace(".", "").length();
                if (decimalPoints > 1) {
                    int firstDecimal = text.indexOf(".");
                    String corrected = text.substring(0, firstDecimal + 1) + 
                                     text.substring(firstDecimal + 1).replace(".", "");
                    inputValue1.setText(corrected);
                    inputValue1.setSelection(corrected.length());
                }
            }
        });

        inputValue1.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                inputValue2.setText("");
            }
        });
    }

    private void convert() {
        String valueStr = inputValue1.getText().toString().trim();
        
        if (valueStr.isEmpty() || valueStr.equals("-") || valueStr.equals(".")) {
            inputValue2.setText("");
            return;
        }

        try {
            double value = Double.parseDouble(valueStr);
            String fromUnit = unit1.getText().toString();
            String toUnit = unit2.getText().toString();

            Double fromRate = conversionRates.get(fromUnit);
            Double toRate = conversionRates.get(toUnit);

            if (fromRate == null || toRate == null) {
                inputValue2.setText("");
                return;
            }

            // Convert to base unit (meters) first, then to target unit
            double valueInMeters = value / fromRate;
            double result = valueInMeters * toRate;

            // Format the result
            String formattedResult = decimalFormat.format(result);
            inputValue2.setText(formattedResult);
        } catch (NumberFormatException e) {
            inputValue2.setText("");
        }
    }
}
