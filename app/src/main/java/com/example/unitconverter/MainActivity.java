package com.example.unitconverter;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity {

    private EditText inputValue1;
    private TextView inputValue2;
    private AutoCompleteTextView unit1, unit2;
    private TextView unitDisplay1, unitDisplay2;
    private boolean isInput1Active = true;

    // Conversion rates for length units (base unit: meters)
    private final Map<String, Double> conversionRates = new HashMap<>();
    private final Map<String, String> unitSymbols = new HashMap<>();
    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.##");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupConversionRates();
        setupUnitSelectors();
        setupInputListeners();
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
        conversionRates.put("Meters", 1.0);
        conversionRates.put("Centimeters", 100.0);
        conversionRates.put("Feet", 3.28084);
        conversionRates.put("Inches", 39.3701);
        conversionRates.put("Yards", 1.09361);

        // Unit symbols
        unitSymbols.put("Meters", getString(R.string.meters_symbol));
        unitSymbols.put("Centimeters", getString(R.string.centimeters_symbol));
        unitSymbols.put("Feet", "ft");
        unitSymbols.put("Inches", "in");
        unitSymbols.put("Yards", "yd");
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
        unit1.setText("Meters", false);
        unit2.setText("Centimeters", false);
        unitDisplay1.setText(unitSymbols.get("Meters"));
        unitDisplay2.setText(unitSymbols.get("Centimeters"));

        // Add listeners for unit changes
        unit1.setOnItemClickListener((parent, view, position, id) -> {
            String selectedUnit = units[position];
            unitDisplay1.setText(unitSymbols.get(selectedUnit));
            convert();
        });
        
        unit2.setOnItemClickListener((parent, view, position, id) -> {
            String selectedUnit = units[position];
            unitDisplay2.setText(unitSymbols.get(selectedUnit));
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
                isInput1Active = true;
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

            // Convert to base unit (meters) first, then to target unit
            double valueInMeters = value / conversionRates.get(fromUnit);
            double result = valueInMeters * conversionRates.get(toUnit);

            // Format the result
            String formattedResult = decimalFormat.format(result);
            inputValue2.setText(formattedResult);
        } catch (NumberFormatException | NullPointerException e) {
            inputValue2.setText("");
        }
    }
}
