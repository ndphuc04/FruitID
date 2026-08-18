package com.example.fruitid;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import android.widget.Button;

public class AccountFragment extends Fragment {

    private TextView tvAvatarLarge, tvAccountName, tvAccountEmail;
    private LinearLayout btnChangeName, btnChangePassword, btnClearHistory, btnAbout, btnLogout;
    private SharedPreferences prefs;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_account, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 1. Ánh xạ các thành phần giao diện
        tvAvatarLarge = view.findViewById(R.id.tvAvatarLarge);
        tvAccountName = view.findViewById(R.id.tvAccountName);
        tvAccountEmail = view.findViewById(R.id.tvAccountEmail);

        btnChangeName = view.findViewById(R.id.btnChangeName);
        btnChangePassword = view.findViewById(R.id.btnChangePassword); // Ánh xạ nút đổi mật khẩu
        btnClearHistory = view.findViewById(R.id.btnClearHistory);
        btnAbout = view.findViewById(R.id.btnAbout);
        btnLogout = view.findViewById(R.id.btnLogout);

        prefs = requireActivity().getSharedPreferences(WelcomeActivity.PREFS_NAME, Context.MODE_PRIVATE);

        loadUserInfo();

        setupListeners();
    }

    private void loadUserInfo() {
        String userName = prefs.getString(WelcomeActivity.KEY_NAME, "Người dùng");
        tvAccountName.setText(userName);

        if (userName != null && !userName.trim().isEmpty()) {
            String firstLetter = String.valueOf(userName.trim().charAt(0)).toUpperCase();
            tvAvatarLarge.setText(firstLetter);
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getEmail() != null) {
            tvAccountEmail.setText(currentUser.getEmail());
        } else {
            tvAccountEmail.setText("Chưa liên kết Email");
        }
    }

    private void setupListeners() {
        btnChangeName.setOnClickListener(v -> showChangeNameDialog());
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
        btnClearHistory.setOnClickListener(v -> showClearHistoryDialog());
        btnAbout.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Ứng dụng from nhóm 1 hihi \uD83D\uDE1C", Toast.LENGTH_SHORT).show();
        });
        btnLogout.setOnClickListener(v -> showLogoutDialog());
    }

    //  logic đổi mật khẩu
    private void showChangePasswordDialog() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) return;

        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password, null);
        EditText etCurrentPass = view.findViewById(R.id.etCurrentPass);
        EditText etNewPass = view.findViewById(R.id.etNewPass);
        EditText etConfirmPass = view.findViewById(R.id.etConfirmPass);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Bảo mật tài khoản")
                .setView(view)
                .setPositiveButton("Cập nhật", null)
                .setNegativeButton("Hủy", (d, w) -> d.cancel())
                .create();

        // Xử lý logic khi bấm nút Cập nhật
        dialog.setOnShowListener(dialogInterface -> {
            Button btnUpdate = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            btnUpdate.setOnClickListener(v -> {
                String currentPass = etCurrentPass.getText().toString().trim();
                String newPass = etNewPass.getText().toString().trim();
                String confirmPass = etConfirmPass.getText().toString().trim();

                if (currentPass.isEmpty() || newPass.isEmpty() || confirmPass.isEmpty()) {
                    Toast.makeText(requireContext(), "Vui lòng điền kín các ô!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (newPass.length() < 6) {
                    Toast.makeText(requireContext(), "Mật khẩu mới phải từ 6 ký tự!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!newPass.equals(confirmPass)) {
                    Toast.makeText(requireContext(), "Hai mật khẩu mới không khớp nhau!", Toast.LENGTH_SHORT).show();
                    return;
                }

                AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPass);

                user.reauthenticate(credential).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {

                        user.updatePassword(newPass).addOnCompleteListener(updateTask -> {
                            if (updateTask.isSuccessful()) {
                                Toast.makeText(requireContext(), "Đổi mật khẩu thành công!", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            } else {
                                Toast.makeText(requireContext(), "Lỗi khi cập nhật mật khẩu!", Toast.LENGTH_SHORT).show();
                            }
                        });

                    } else {
                        etCurrentPass.setError("Mật khẩu hiện tại không đúng!");
                        etCurrentPass.requestFocus();
                    }
                });
            });
        });
        dialog.show();
    }

    private void showChangeNameDialog() {
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint("Nhập tên mới của bạn");

        String currentName = prefs.getString(WelcomeActivity.KEY_NAME, "");
        input.setText(currentName);
        input.setSelection(currentName.length());

        new AlertDialog.Builder(requireContext())
                .setTitle("Đổi tên hiển thị")
                .setView(input)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        prefs.edit().putString(WelcomeActivity.KEY_NAME, newName).apply();

                        FirebaseAuth auth = FirebaseAuth.getInstance();
                        if (auth.getCurrentUser() != null) {
                            FirebaseFirestore.getInstance()
                                    .collection("Users")
                                    .document(auth.getCurrentUser().getUid())
                                    .update("name", newName);
                        }

                        loadUserInfo();
                        Toast.makeText(requireContext(), "Đã đổi tên thành công!", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Tên không được để trống!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Hủy", (dialog, which) -> dialog.cancel())
                .show();
    }

    private void showClearHistoryDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa lịch sử")
                .setMessage("Bạn có chắc chắn muốn xóa toàn bộ lịch sử quét trên đám mây không?")
                .setPositiveButton("Xóa tất cả", (dialog, which) -> {

                    FirebaseAuth auth = FirebaseAuth.getInstance();
                    if (auth.getCurrentUser() != null) {
                        String uid = auth.getCurrentUser().getUid();
                        FirebaseFirestore db = FirebaseFirestore.getInstance();

                        db.collection("Users").document(uid).collection("History")
                                .get()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        for (QueryDocumentSnapshot document : task.getResult()) {
                                            document.getReference().delete();
                                        }
                                        Toast.makeText(requireContext(), "Đã dọn sạch lịch sử trên đám mây!", Toast.LENGTH_SHORT).show();
                                        getParentFragmentManager().setFragmentResult("clear_history_request", new Bundle());
                                    } else {
                                        Toast.makeText(requireContext(), "Lỗi khi xóa: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất khỏi ứng dụng?")
                .setPositiveButton("Đăng xuất", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    prefs.edit().clear().apply();
                    Intent intent = new Intent(requireActivity(), LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }
}