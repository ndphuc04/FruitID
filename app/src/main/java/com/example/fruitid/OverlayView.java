package com.example.fruitid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class OverlayView extends View {
    private List<FruitClassifier.BoxResult> results = new ArrayList<>();
    private Paint boxPaint;
    private Paint textBackgroundPaint;
    private Paint textPaint;

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    private void initPaints() {
        // Cọ vẽ khung chữ nhật
        boxPaint = new Paint();
        boxPaint.setColor(Color.GREEN);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(8f);

        // Cọ vẽ nền chữ
        textBackgroundPaint = new Paint();
        textBackgroundPaint.setColor(Color.parseColor("#80000000"));
        textBackgroundPaint.setStyle(Paint.Style.FILL);

        // Cọ vẽ chữ
        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(50f);
        textPaint.setFakeBoldText(true);
    }

    public void setResults(List<FruitClassifier.BoxResult> newResults) {
        this.results = newResults;
        invalidate(); // Báo cho Android biết cần gọi lại onDraw() để vẽ frame mới
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (results == null || results.isEmpty()) return;

        // Lấy kích thước thực tế của màn hình  hiện tại
        int viewWidth = getWidth();
        int viewHeight = getHeight();

        for (FruitClassifier.BoxResult result : results) {
            // CHUẨN HOÁ TỌA ĐỘ: Nhân tỷ lệ (0.0 - 1.0) với chiều rộng và chiều cao màn hình
            float left = result.boundingBox.left * viewWidth;
            float top = result.boundingBox.top * viewHeight;
            float right = result.boundingBox.right * viewWidth;
            float bottom = result.boundingBox.bottom * viewHeight;

            // VẼ KHUNG CHỮ NHẬT BÁM VÀO QUẢ
            canvas.drawRect(left, top, right, bottom, boxPaint);

            // VẼ NHÃN TÊN VÀ % CHÍNH XÁC
            String label = result.fruitName + " " + String.format("%.1f", result.accuracy) + "%";

            // Xử lý chống tràn viền: Nếu sát mép trên quá, đẩy chữ lùi xuống dưới viền khung
            float textY = top - 15f;
            if (textY < 60f) {
                textY = top + 60f;
            }

            // Đo chiều dài đoạn Text để vẽ nền đen cho vừa vặn
            float textWidth = textPaint.measureText(label);

            // Vẽ phông nền đen
            canvas.drawRect(left, textY - 50f, left + textWidth + 20f, textY + 15f, textBackgroundPaint);

            // Vẽ chữ trắng đè lên phông nền
            canvas.drawText(label, left + 10f, textY, textPaint);
        }
    }
}