package org.ugd.epp;

import android.graphics.Rect;

import org.pytorch.demo.objectdetection.Result;

public class EPPResult extends Result {
    private String customText;
    private boolean ok;

    public EPPResult(Result r, String customText, boolean ok) {
        super(r.getClassIndex(), r.getScore(), r.getRect());
        this.customText = customText;
        this.ok = ok;
    }

    public String getCustomText() {
        return customText;
    }

    public boolean isOk() {
        return ok;
    }
}
