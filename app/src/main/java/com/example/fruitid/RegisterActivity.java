package com.example.fruitid;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;

public class RegisterActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        etEmail = findViewById(R.id.etRegEmail);
        etPassword = findViewById(R.id.etRegPassword);

        // Nút Đăng ký
        findViewById(R.id.btnRegister).setOnClickListener(v -> registerUser());

        // Nút quay lại Đăng nhập
        findViewById(R.id.tvGoToLogin).setOnClickListener(v -> finish());
    }

    private void registerUser() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.length() < 6) {
            Toast.makeText(this, "Vui lòng nhập Email và Mật khẩu (ít nhất 6 ký tự)", Toast.LENGTH_SHORT).show();
            return;
        }

        // Gọi API của Firebase để Tạo tài khoản
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Đăng ký thành công! Hãy thiết lập hồ sơ.", Toast.LENGTH_SHORT).show();
                        // Đăng ký xong, đẩy qua màn hình Welcome để chọn Mục tiêu
                        startActivity(new Intent(RegisterActivity.this, WelcomeActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Lỗi: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}