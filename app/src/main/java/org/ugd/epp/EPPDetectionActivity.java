package org.ugd.epp;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.util.Log;
import android.util.Pair;
import android.view.TextureView;
import android.view.ViewStub;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.camera.core.ImageProxy;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.demo.objectdetection.AbstractCameraXActivity;
import org.pytorch.demo.objectdetection.MainActivity;
import org.pytorch.demo.objectdetection.PrePostProcessor;
import org.pytorch.demo.objectdetection.R;
import org.pytorch.demo.objectdetection.Result;
import org.pytorch.demo.objectdetection.ResultView;
import org.pytorch.demo.objectdetection.Triple;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class EPPDetectionActivity extends AbstractCameraXActivity<EPPDetectionActivity.AnalysisResult> {
    private Module mModule = null;
    private EPPResultView mResultView;

    static class AnalysisResult {
        private final ArrayList<EPPResult> mResults;

        public AnalysisResult(ArrayList<EPPResult> results) {
            mResults = results;
        }
    }

    @Override
    protected int getContentViewLayoutId() {
        return R.layout.activity_epp_detection;
    }

    @Override
    protected TextureView getCameraPreviewTextureView() {
        mResultView = findViewById(R.id.resultView);
        return ((ViewStub) findViewById(R.id.object_detection_texture_view_stub))
                .inflate()
                .findViewById(R.id.object_detection_texture_view);
    }

    @Override
    protected void applyToUiAnalyzeImageResult(AnalysisResult result) {
        mResultView.setResults(result.mResults);
        mResultView.invalidate();
    }

    private Bitmap imgToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    @Override
    @WorkerThread
    @Nullable
    protected AnalysisResult analyzeImage(ImageProxy image, int rotationDegrees) {
        try {
            if (mModule == null) {
//                mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), "yolov5s.torchscript.ptl"));
                mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), "yolov5snulls.torchscript.ptl"));
            }
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            return null;
        }
        Bitmap bitmap = imgToBitmap(image.getImage());
        Matrix matrix = new Matrix();
        matrix.postRotate(90.0f);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
//        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);
        Pair<Integer, Integer> newShape = new Pair<>(PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight);
        Triple<Integer, Integer, Integer> color = new Triple<>(114, 114, 114);
        Bitmap resizedBitmap = this.letterbox(bitmap, newShape, color, true, true, true, 32);

        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
        IValue[] outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple();
        final Tensor outputTensor = outputTuple[0].toTensor();
        final float[] outputs = outputTensor.getDataAsFloatArray();

        float imgScaleX = (float)bitmap.getWidth() / PrePostProcessor.mInputWidth;
        float imgScaleY = (float)bitmap.getHeight() / PrePostProcessor.mInputHeight;
        float ivScaleX = (float)mResultView.getWidth() / bitmap.getWidth();
        float ivScaleY = (float)mResultView.getHeight() / bitmap.getHeight();

        final ArrayList<Result> results = PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, 0, 0);
        AlgoritmoDecision algoritmoDecision = new AlgoritmoDecision(results, false);
        return new AnalysisResult(algoritmoDecision.getResultadosProcesados());
    }

    /**
     * Image augmentations: first scale the image and later padding image,  increase the strength of the model.
     * Always this scale and padding will make the image object detection gave more high probability or more robust.
     * <p>
     * Reference:
     * https://github.com/ultralytics/yolov5/blob/db6ec66a602a0b64a7db1711acd064eda5daf2b3/utils/augmentations.py#L91-L122
     * def letterbox(im, new_shape=(640, 640), color=(114, 114, 114), auto=True, scaleFill=False, scaleup=True, stride=32):
     * method
     *
     * @param srcBitmap
     * @param newShape  (640*640)
     * @param color     always gray (114,114,114)
     * @param auto      default:false, no use
     * @param scaleFill default:false,  no use
     * @param scaleUp   default:false
     * @param stride    default:32 , no use
     * @return
     */
    private Bitmap letterbox(Bitmap srcBitmap, Pair<Integer, Integer> newShape, Triple<Integer, Integer, Integer> color, Boolean auto,
                                   Boolean scaleFill, Boolean scaleUp, int stride) {
        // current shape
        int currentWidth = srcBitmap.getWidth();
        int currentHeight = srcBitmap.getHeight();

        // new shape eg: 640*640
        int newWidth = newShape.first;
        int newHeight = newShape.second;

        // only scale image，no padding,just return scale image
        // I modify this logic something difference with the python code clean & speed.
        if (scaleFill) {
            // filter =  bilinear filtering
            return Bitmap.createScaledBitmap(srcBitmap, newWidth, newHeight, true);
        }

        // Scale ratio (new / old)
        float r = Math.min(newWidth * 1.0f / currentWidth, newHeight * 1.0f / currentHeight);

        //  Only scale down, do not scale up (for better val mAP)
        if (!scaleUp) {
            r = Math.min(r, 1.0f);
        }

        int newUnpadWidth = Math.round(currentWidth * r);
        int newUnpadHeight = Math.round(currentHeight * r);

        //  wh padding
        int dw = newWidth - newUnpadWidth;
        int dh = newHeight - newUnpadHeight;

        // auto always false, no use for android demo
        if (auto) { // # wh padding
            dw = dw % stride;
            dh = dh % stride;
        }

        // resize
        if (!(currentWidth == newUnpadWidth && currentHeight == newUnpadHeight)) {
            srcBitmap = Bitmap.createScaledBitmap(srcBitmap, newUnpadWidth, newUnpadHeight, true);
        }

        // padding with gray color
        Bitmap outBitmap = Bitmap.createBitmap(srcBitmap.getWidth() + dw, srcBitmap.getHeight() + dh, Bitmap.Config.ARGB_8888);
        Canvas can = new Canvas(outBitmap);
        can.drawRGB(color.getFirst(), color.getSecond(), color.getThird()); // gray color
        can.drawBitmap(srcBitmap, dw, dh, null);

        return outBitmap;
    }
}
