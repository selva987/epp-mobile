package org.pytorch.demo.objectdetection;

import android.graphics.Rect;

public class Result {
    int classIndex;
    Float score;
    Rect rect;

    public Result(int cls, Float output, Rect rect) {
        this.classIndex = cls;
        this.score = output;
        this.rect = rect;
    }

    public int getClassIndex() {
        return classIndex;
    }

    public Float getScore() {
        return score;
    }

    public Rect getRect() {
        return rect;
    }
}