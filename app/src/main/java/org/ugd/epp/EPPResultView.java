package org.ugd.epp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import org.pytorch.demo.objectdetection.PrePostProcessor;

import java.util.ArrayList;

public class EPPResultView extends View {

    private ArrayList<EPPResult> mResults;

    private final static int TEXT_X = 40;
    private final static int TEXT_Y = 35;
    private final static int TEXT_WIDTH = 100;
    private final static int TEXT_HEIGHT = 50;

    private Paint mPaintRectangle;
    private Paint mPaintText;


    public EPPResultView(Context context) {
        super(context);
    }

    public EPPResultView(Context context, AttributeSet attrs){
        super(context, attrs);
        mPaintRectangle = new Paint();
        mPaintText = new Paint();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mResults == null) return;
        for (EPPResult result : mResults) {

            //Si esta bien va a ser verde, sino rojo
            int color = result.isOk() ? Color.GREEN : Color.RED;

            //Dibujar recuadro de persona
            this.mPaintRectangle.setStrokeWidth(5);
            this.mPaintRectangle.setStyle(Paint.Style.STROKE);
            this.mPaintRectangle.setColor(color);
            canvas.drawRect(result.getRect(), this.mPaintRectangle);

            //Dibujar recuadro de leyenda
            Path mPath = new Path();
            RectF mRectF = new RectF(result.getRect().left, result.getRect().top, result.getRect().right,  result.getRect().top + TEXT_HEIGHT);
            mPath.addRect(mRectF, Path.Direction.CW);
            this.mPaintText.setColor(color);
            canvas.drawPath(mPath, this.mPaintText);

            //Escribir leyenda
            this.mPaintText.setColor(Color.WHITE);
            this.mPaintText.setStrokeWidth(0);
            this.mPaintText.setStyle(Paint.Style.FILL);
            this.mPaintText.setTextSize(32);
            canvas.drawText(result.getCustomText(), result.getRect().left + TEXT_X, result.getRect().top + TEXT_Y, this.mPaintText);
        }
    }

    public void setResults(ArrayList<EPPResult> results) {
        this.mResults = results;
    }

}
