package com.example.fruitid;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    private static final SimpleDateFormat SCAN_DISPLAY_FORMAT =
            new SimpleDateFormat("dd/MM/yyyy - HH:mm", Locale.getDefault());

    private ImageView imgSearch;
    private TextView tvNote;
    private RecyclerView rvHistory;

    private HistoryAdapter adapter;
    private List<HistoryItem> historyList;

    private ListenerRegistration historyListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        imgSearch = view.findViewById(R.id.imgSearch);
        tvNote = view.findViewById(R.id.tvNote);
        rvHistory = view.findViewById(R.id.rvHistory);

        historyList = new ArrayList<>();

        adapter = new HistoryAdapter(historyList, (item, position) -> {
            showDeleteSingleItemDialog(item, position);
        });

        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvHistory.setAdapter(adapter);

        getParentFragmentManager().setFragmentResultListener("clear_history_request", this,
                (requestKey, result) -> attachRealtimeListener());

        attachRealtimeListener();
    }

    private void showDeleteSingleItemDialog(HistoryItem item, int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xóa lịch sử này?")
                .setMessage("Bạn có chắc chắn muốn xóa kết quả: " + item.getFruitName() + " không?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    deleteItemFromFirebase(item, position);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteItemFromFirebase(HistoryItem item, int position) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null || item.getDocumentId() == null) return;

        String uid = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(uid).collection("History").document(item.getDocumentId())
                .delete()
                .addOnSuccessListener(aVoid -> {

                    if (item.getImagePath() != null && !item.getImagePath().isEmpty()) {
                        if (item.getImagePath().startsWith("http")) {
                            FirebaseStorage.getInstance().getReferenceFromUrl(item.getImagePath())
                                    .delete()
                                    .addOnSuccessListener(aVoid1 -> Log.d("Firebase", "Đã xóa ảnh trên Storage"))
                                    .addOnFailureListener(e -> Log.e("Firebase", "Lỗi xóa ảnh Storage", e));
                        } else if (item.getImagePath().startsWith("/")) {
                            File imgFile = new File(item.getImagePath());
                            if (imgFile.exists()) {
                                imgFile.delete();
                                Log.d("Local", "Đã xóa ảnh dưới máy");
                            }
                        } else {
                            Log.d("Firebase", "Ảnh dạng Base64, không cần xóa file tĩnh");
                        }
                    }

                    Toast.makeText(requireContext(), "Đã xóa thành công!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Lỗi khi xóa: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // Hàm đọc dữ liệu dạng Real-time
    private void attachRealtimeListener() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            historyList.clear();
            adapter.notifyDataSetChanged();
            updateEmptyState();
            return;
        }

        String uid = auth.getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (historyListener != null) {
            historyListener.remove();
        }

        historyListener = db.collection("Users").document(uid).collection("History")
                .addSnapshotListener((value, error) -> {
                    if (!isAdded()) return;

                    if (error != null) {
                        Log.e("Firebase", "Lỗi tải lịch sử: ", error);
                        Toast.makeText(requireContext(), "Không tải được lịch sử: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }

                    if (value != null) {
                        List<QueryDocumentSnapshot> docs = new ArrayList<>();
                        for (QueryDocumentSnapshot doc : value) {
                            docs.add(doc);
                        }

                        Collections.sort(docs, (a, b) -> Long.compare(getScanDateMillis(b), getScanDateMillis(a)));

                        historyList.clear();
                        for (QueryDocumentSnapshot document : docs) {
                            String name = document.getString("fruitName");
                            Double accDouble = document.getDouble("accuracy");
                            float acc = accDouble != null ? accDouble.floatValue() : 0f;

                            Object dateObj = document.get("scanDate");
                            String date = "";
                            if (dateObj instanceof String) {
                                date = (String) dateObj;
                            } else if (dateObj instanceof Timestamp) {
                                date = formatScanDate(((Timestamp) dateObj).toDate().getTime());
                            } else if (dateObj instanceof Long) {
                                date = formatScanDate((Long) dateObj);
                            }

                            String imgPath = document.getString("imagePath");

                            HistoryItem item = new HistoryItem(0, name, acc, date, imgPath);
                            item.setDocumentId(document.getId());
                            historyList.add(item);
                        }
                        adapter.notifyDataSetChanged();
                        updateEmptyState();
                    }
                });
    }

    private static long getScanDateMillis(QueryDocumentSnapshot document) {
        Object dateObj = document.get("scanDate");
        if (dateObj instanceof String) {
            try {
                return SCAN_DISPLAY_FORMAT.parse((String) dateObj).getTime();
            } catch (ParseException e) {
                return 0L;
            }
        } else if (dateObj instanceof Timestamp) {
            return ((Timestamp) dateObj).toDate().getTime();
        } else if (dateObj instanceof Long) {
            return (Long) dateObj;
        }
        return 0L;
    }

    private static String formatScanDate(long millis) {
        return SCAN_DISPLAY_FORMAT.format(new Date(millis));
    }

    private void updateEmptyState() {
        if (historyList.isEmpty()) {
            imgSearch.setVisibility(View.VISIBLE);
            tvNote.setVisibility(View.VISIBLE);
            rvHistory.setVisibility(View.GONE);
        } else {
            imgSearch.setVisibility(View.GONE);
            tvNote.setVisibility(View.GONE);
            rvHistory.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (historyListener != null) {
            historyListener.remove();
            historyListener = null;
        }
    }

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        private final List<HistoryItem> items;
        private final OnItemLongClickListener listener;

        public interface OnItemLongClickListener {
            void onLongClick(HistoryItem item, int position);
        }

        public HistoryAdapter(List<HistoryItem> items, OnItemLongClickListener listener) {
            this.items = items;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            HistoryItem item = items.get(position);

            holder.tvFruitName.setText(item.getFruitName());
            holder.tvScanDate.setText(item.getScanDate());

            String imagePath = item.getImagePath();

            if (imagePath != null && !imagePath.isEmpty()) {

                if (imagePath.length() > 500 || imagePath.startsWith("/9j/")) {
                    try {
                        byte[] decodedString = android.util.Base64.decode(imagePath, android.util.Base64.DEFAULT);
                        android.graphics.Bitmap decodedByte = android.graphics.BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

                        Glide.with(holder.itemView.getContext())
                                .asBitmap()
                                .load(decodedByte)
                                .centerCrop()
                                .circleCrop()
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .error(android.R.drawable.ic_menu_report_image)
                                .into(holder.imgThumbnail);

                    } catch (Exception e) {
                        Log.e("LoadImage", "Lỗi giải mã ảnh Base64", e);
                        holder.imgThumbnail.setImageResource(android.R.drawable.ic_menu_report_image);
                    }
                }
                else {
                    Glide.with(holder.itemView.getContext())
                            .load(imagePath)
                            .centerCrop()
                            .circleCrop()
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_report_image)
                            .into(holder.imgThumbnail);
                }
            } else {
                holder.imgThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            holder.itemView.setOnLongClickListener(v -> {
                int currentPosition = holder.getBindingAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION && listener != null) {
                    listener.onLongClick(item, currentPosition);
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgThumbnail;
            TextView tvFruitName, tvScanDate;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgThumbnail = itemView.findViewById(R.id.imgThumbnail);
                tvFruitName = itemView.findViewById(R.id.tvFruitName);
                tvScanDate = itemView.findViewById(R.id.tvScanDate);
            }
        }
    }
}