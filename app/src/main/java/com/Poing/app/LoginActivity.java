package com.Poing.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private EditText emailField, passwordField;
    private boolean passwordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAuth = FirebaseAuth.getInstance();

        // If already logged in, skip straight to friends screen
        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(this, FriendsActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        emailField = findViewById(R.id.emailField);
        passwordField = findViewById(R.id.passwordField);
        Button loginButton = findViewById(R.id.loginButton);
        TextView goToRegister = findViewById(R.id.goToRegister);
        ImageButton eyeButton = findViewById(R.id.eyeButton);

        // Eye button toggles password visibility
        eyeButton.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            if (passwordVisible) {
                passwordField.setInputType(InputType.TYPE_CLASS_TEXT);
                eyeButton.setImageResource(android.R.drawable.ic_menu_view);
            } else {
                passwordField.setInputType(InputType.TYPE_CLASS_TEXT |
                        InputType.TYPE_TEXT_VARIATION_PASSWORD);
                eyeButton.setImageResource(android.R.drawable.ic_secure);
            }
            passwordField.setSelection(passwordField.getText().length());
        });

        loginButton.setOnClickListener(v -> attemptLogin());
        goToRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    private void attemptLogin() {
        String email = emailField.getText().toString().trim();
        String password = passwordField.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    startActivity(new Intent(this, FriendsActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Login failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show());
    }
}