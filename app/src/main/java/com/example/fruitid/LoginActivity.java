package com.example.fruitid;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private View loadingOverlay;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // Ánh xạ View
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvGoToRegister);
        loadingOverlay = findViewById(R.id.loadingOverlay); // Ánh xạ lớp phủ

        btnLogin.setOnClickListener(v -> loginUser());

        tvRegister.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            // đã đăng nhập từ trước hiện overlay che đăng nhập
            loadingOverlay.setVisibility(View.VISIBLE);
            fetchProfileAndGoToMain(currentUser.getUid());
        }
    }

    private void loginUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ Email và Mật khẩu", Toast.LENGTH_SHORT).show();
            return;
        }

        loadingOverlay.setVisibility(View.VISIBLE);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            fetchProfileAndGoToMain(user.getUid());
                        }
                    } else {
                        loadingOverlay.setVisibility(View.GONE);
                        Toast.makeText(this, "Lỗi đăng nhập: Sai email hoặc mật khẩu", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void fetchProfileAndGoToMain(String uid) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Lấy dữ liệu từ đám mây xuống
                        String name = documentSnapshot.getString("name");
                        String goals = documentSnapshot.getString("goals");

                        // Lưu đè vào bộ nhớ đệm của máy hiện tại
                        getSharedPreferences(WelcomeActivity.PREFS_NAME, Context.MODE_PRIVATE)
                                .edit()
                                .putString(WelcomeActivity.KEY_NAME, name != null ? name : "Người dùng")
                                .putString(WelcomeActivity.KEY_GOALS, goals != null ? goals : "")
                                .putBoolean(WelcomeActivity.KEY_WELCOMED, true)
                                .apply();

                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    } else {
                        startActivity(new Intent(LoginActivity.this, WelcomeActivity.class));
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    // Nếu lỗi mạng, giấu lớp phủ đi và cho vào app tĩnh
                    loadingOverlay.setVisibility(View.GONE);
                    Toast.makeText(this, "Chưa thể đồng bộ tên: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    finish();
                });
    }
}