package com.Poing.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences prefs2 = getSharedPreferences("poing_prefs", MODE_PRIVATE);
        String theme = prefs2.getString("theme", "default");
        if (theme.equals("dark")) setTheme(R.style.Theme_Poing_Dark);
        else if (theme.equals("light")) setTheme(R.style.Theme_Poing_Light);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        FirebaseAuth auth = FirebaseAuth.getInstance();
        SharedPreferences prefs = getSharedPreferences("poing_prefs", MODE_PRIVATE);

        RadioGroup themeGroup = findViewById(R.id.themeGroup);
        RadioButton themeDefault = findViewById(R.id.themeDefault);
        RadioButton themeLight = findViewById(R.id.themeLight);
        RadioButton themeDark = findViewById(R.id.themeDark);
        Button logoutButton = findViewById(R.id.logoutButton);
        TextView loggedInAsText = findViewById(R.id.loggedInAsText);

        // Show current email
        if (auth.getCurrentUser() != null) {
            loggedInAsText.setText("Logged in as: " + auth.getCurrentUser().getEmail());
        }

        // Load saved theme
        String savedTheme = prefs.getString("theme", "default");
        if (savedTheme.equals("dark")) themeDark.setChecked(true);
        else if (savedTheme.equals("light")) themeLight.setChecked(true);
        else themeDefault.setChecked(true);

        // Save theme on change
        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String selected;
            if (checkedId == R.id.themeDark) selected = "dark";
            else if (checkedId == R.id.themeLight) selected = "light";
            else selected = "default";

            prefs.edit().putString("theme", selected).apply();

            // Restart the entire app from login to apply theme everywhere cleanly
            Intent intent = new Intent(this, FriendsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        logoutButton.setOnClickListener(v -> {
            auth.signOut();
            stopService(new Intent(this, LocationService.class));
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }
}