package com.example.fruitid;

import android.content.Context;
import android.graphics.Bitmap;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import android.graphics.RectF;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FruitClassifier {

    private Interpreter interpreter;
    private List<String> labels;

    // Kích thước cấu hình ảnh đầu vào khớp với  Pythofile trainn của bạn
    private static final int IMAGE_SIZE_X = 640;
    private static final int IMAGE_SIZE_Y = 640;

    // Ngưỡng tin cậy và ngưỡng lọc khung hình trùng lặp
    private static final float CONFIDENCE_THRESHOLD = 0.40f;
    private static final float IOU_THRESHOLD = 0.45f;

    public FruitClassifier(Context context) throws IOException {
        MappedByteBuffer modelFile = FileUtil.loadMappedFile(context, "fruit_model.tflite");
        labels = FileUtil.loadLabels(context, "labels.txt");

        Interpreter.Options options = new Interpreter.Options();
        options.addDelegate(new GpuDelegate());
        
        interpreter = new Interpreter(modelFile, options);
    }

    // Cấu trúc dữ liệu lưu trữ các khung hình dự đoán tạm thời
    private static class Prediction implements Comparable<Prediction> {
        float x1, y1, x2, y2, score;
        int classIndex;

        public Prediction(float x1, float y1, float x2, float y2, float score, int classIndex) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
            this.score = score;
            this.classIndex = classIndex;
        }

        @Override
        public int compareTo(Prediction o) {
            // Sắp xếp giảm dần theo điểm số để phục vụ thuật toán NMS
            return Float.compare(o.score, this.score);
        }
    }

    // Đối tượng kết quả trả về cho luồng chính của App
    public static class BoxResult {
        public String fruitName;
        public float accuracy;
        public RectF boundingBox;

        public BoxResult(String fruitName, float accuracy, RectF boundingBox) {
            this.fruitName = fruitName;
            this.accuracy = accuracy;
            this.boundingBox = boundingBox;
        }
    }

    public List<BoxResult> recognizeImage(Bitmap bitmap) {
        // Tiền xử lý ảnh và chuẩn hóa trực tiếp trên bộ nhớ Buffer gốc
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(IMAGE_SIZE_X, IMAGE_SIZE_Y, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(0.0f, 255.0f)) // Chia trực tiếp cho 255.0f để đưa về khoảng 0.0 -> 1.0
                .build();

        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(bitmap);
        tensorImage = imageProcessor.process(tensorImage);

        // Thiết lập mảng hứng dữ liệu đầu ra chuẩn YOLOv8 [1][16][8400]
        int numClasses = labels.size();
        int numElements = 4 + numClasses; // 4 tọa độ + 12 loại quả = 16 phần tử
        float[][][] outputArray = new float[1][numElements][8400];

        // Kích hoạt mô hình AI chạy phân tích ma trận
        if (interpreter != null) {
            interpreter.run(tensorImage.getBuffer(), outputArray);
        }

        // 3. Duyệt qua 8400 ô lưới để bóc tách xác suất
        List<Prediction> predictions = new ArrayList<>();

        for (int i = 0; i < 8400; i++) {
            float maxScore = 0f;
            int bestClassIdx = -1;

            // Quét qua tất cả các lớp trái cây xem lớp nào điểm cao nhất ở ô lưới này
            for (int c = 0; c < numClasses; c++) {
                float score = outputArray[0][4 + c][i];
                if (score > maxScore) {
                    maxScore = score;
                    bestClassIdx = c;
                }
            }

            // Nếu vượt qua ngưỡng tin cậy thì tính toán tọa độ Bounding Box của quả đó
            if (maxScore > CONFIDENCE_THRESHOLD) {
                float cx = outputArray[0][0][i];
                float cy = outputArray[0][1][i];
                float w = outputArray[0][2][i];
                float h = outputArray[0][3][i];


                // Tọa độ từ TFLite YOLOv8 mặc định chuẩn hóa (0.0 -> 1.0)
                float x1 = Math.max(0f, cx - w / 2f);
                float y1 = Math.max(0f, cy - h / 2f);
                float x2 = Math.min(1f, cx + w / 2f);
                float y2 = Math.min(1f, cy + h / 2f);

                predictions.add(new Prediction(x1, y1, x2, y2, maxScore, bestClassIdx));
            }
        }

        //  Áp dụng thuật toán NMS lọc bỏ các khung trùng lặp, giữ lại khung tối ưu nhất
        List<Prediction> nmsResults = applyNMS(predictions);

        //  Kết luận dữ liệu thực tế để đưa ra màn hình kết quả
        List<BoxResult> results = new ArrayList<>();
        for (Prediction p : nmsResults) {
            String fruitName = labels.get(p.classIndex);
            float accuracy = p.score * 100f; // Trả về con số % thực tế chính xác
            RectF rectF = new RectF(p.x1, p.y1, p.x2, p.y2);
            results.add(new BoxResult(fruitName, accuracy, rectF));
        }

        return results;
    }

    // THUẬT TOÁN KHỬ TRÙNG LẶP KHUNG HÌNH (NON-MAXIMUM SUPPRESSION)
    private List<Prediction> applyNMS(List<Prediction> boxes) {
        List<Prediction> selectedBoxes = new ArrayList<>();
        Collections.sort(boxes); // Sắp xếp điểm số từ cao xuống thấp

        while (!boxes.isEmpty()) {
            Prediction best = boxes.remove(0);
            selectedBoxes.add(best);

            // Xóa tất cả các hộp cùng loại quả có độ đè lên hộp tốt nhất vượt ngưỡng cho phép (IoU > 0.45)
            boxes.removeIf(next -> best.classIndex == next.classIndex && calculateIoU(best, next) > IOU_THRESHOLD);
        }
        return selectedBoxes;
    }

    // HÀM TÍNH TOÁN ĐỘ ĐÈ LÊN NHAU GIỮA 2 BOX
    private float calculateIoU(Prediction boxA, Prediction boxB) {
        float xMin = Math.max(boxA.x1, boxB.x1);
        float yMin = Math.max(boxA.y1, boxB.y1);
        float xMax = Math.min(boxA.x2, boxB.x2);
        float yMax = Math.min(boxA.y2, boxB.y2);

        float interArea = Math.max(0, xMax - xMin) * Math.max(0, yMax - yMin);
        if (interArea == 0) return 0f;

        float areaA = (boxA.x2 - boxA.x1) * (boxA.y2 - boxA.y1);
        float areaB = (boxB.x2 - boxB.x1) * (boxB.y2 - boxB.y1);

        return interArea / (areaA + areaB - interArea);
    }

    public void close() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }
}