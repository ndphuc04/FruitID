package com.example.fruitid;

public class HistoryItem {
    private int id;
    private String fruitName;
    private float accuracy;
    private String scanDate;
    private String imagePath;
    private String documentId;

    // Firebase phải có Constructor rỗng để đọc dữ liệu
    public HistoryItem() {
    }

    public HistoryItem(int id, String fruitName, float accuracy, String scanDate, String imagePath) {
        this.id = id;
        this.fruitName = fruitName;
        this.accuracy = accuracy;
        this.scanDate = scanDate;
        this.imagePath = imagePath;
    }

    public int getId() { return id; }
    public String getFruitName() { return fruitName; }
    public float getAccuracy() { return accuracy; }
    public String getScanDate() { return scanDate; }
    public String getImagePath() { return imagePath; }
    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }
}