// Copyright (c) 2020 Facebook, Inc. and its affiliates.
// All rights reserved.
//
// This source code is licensed under the BSD-style license found in the
// LICENSE file in the root directory of this source tree.

package org.pytorch.demo.objectdetection;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;
import org.ugd.epp.AlgoritmoDecision;
import org.ugd.epp.EPPDetectionActivity;
import org.ugd.epp.EPPResult;
import org.ugd.epp.EPPResultView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements Runnable {
    private int mImageIndex = 0;
    private String[] mTestImages = {
            "test1.png",
            "test2.jpg",
            "test3.png",
            "test4.png",
            "test5.png",
            "test6.JPG",
            "test7.JPG",
            "test8.jpg",
            "test9.jpg",
    };

    private ImageView mImageView;
    private ResultView mResultView;
    private EPPResultView mEppResultView;
    private Button mButtonDetect;
    private Button mEppButtonDetect;
    private ProgressBar mProgressBar;
    private ProgressBar mEppProgressBar;
    private Bitmap mBitmap = null;
    private Module mModule = null;
    private boolean yolo;
    private float mImgScaleX, mImgScaleY, mIvScaleX, mIvScaleY, mStartX, mStartY;

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 1);
        }

        setContentView(R.layout.activity_main);

        try {
            mBitmap = BitmapFactory.decodeStream(getAssets().open(mTestImages[mImageIndex]));
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        }

        mImageView = findViewById(R.id.imageView);
        mImageView.setImageBitmap(mBitmap);

        mResultView = findViewById(R.id.resultView);
        mResultView.setVisibility(View.INVISIBLE);

        mEppResultView = findViewById(R.id.eppResultView);
        mEppResultView.setVisibility(View.INVISIBLE);

        final Button buttonTest = findViewById(R.id.testButton);
        buttonTest.setText(String.format("Prueba 1/%d", mTestImages.length));
        buttonTest.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mResultView.setVisibility(View.INVISIBLE);
                mEppResultView.setVisibility(View.INVISIBLE);
                mImageIndex = (mImageIndex + 1) % mTestImages.length;
                buttonTest.setText(String.format("Prueba %d/%d", mImageIndex + 1, mTestImages.length));

                try {
                    mBitmap = BitmapFactory.decodeStream(getAssets().open(mTestImages[mImageIndex]));
                    mImageView.setImageBitmap(mBitmap);
                } catch (IOException e) {
                    Log.e("Object Detection", "Error reading assets", e);
                    finish();
                }
            }
        });


        final Button buttonSelect = findViewById(R.id.selectButton);
        buttonSelect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mResultView.setVisibility(View.INVISIBLE);
                mEppResultView.setVisibility(View.INVISIBLE);

                final CharSequence[] options = { "Choose from Photos", "Take Picture", "Cancel" };
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("New Test Image");

                builder.setItems(options, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int item) {
                        if (options[item].equals("Take Picture")) {
                            Intent takePicture = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                            startActivityForResult(takePicture, 0);
                        }
                        else if (options[item].equals("Choose from Photos")) {
                            Intent pickPhoto = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
                            startActivityForResult(pickPhoto , 1);
                        }
                        else if (options[item].equals("Cancel")) {
                            dialog.dismiss();
                        }
                    }
                });
                builder.show();
            }
        });

        final Button buttonLive = findViewById(R.id.liveButton);
        buttonLive.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
              final Intent intent = new Intent(MainActivity.this, ObjectDetectionActivity.class);
              startActivity(intent);
            }
        });

        final Button buttonLiveEpp = findViewById(R.id.liveEPPButton);
        buttonLiveEpp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
              final Intent intent = new Intent(MainActivity.this, EPPDetectionActivity.class);
              startActivity(intent);
            }
        });

        mButtonDetect = findViewById(R.id.detectButton);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mButtonDetect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mButtonDetect.setEnabled(false);
                mEppButtonDetect.setEnabled(false);
                mProgressBar.setVisibility(ProgressBar.VISIBLE);
                mButtonDetect.setText(getString(R.string.run_model));

                mImgScaleX = (float)mBitmap.getWidth() / PrePostProcessor.mInputWidth;
                mImgScaleY = (float)mBitmap.getHeight() / PrePostProcessor.mInputHeight;

                mIvScaleX = (mBitmap.getWidth() > mBitmap.getHeight() ? (float)mImageView.getWidth() / mBitmap.getWidth() : (float)mImageView.getHeight() / mBitmap.getHeight());
                mIvScaleY  = (mBitmap.getHeight() > mBitmap.getWidth() ? (float)mImageView.getHeight() / mBitmap.getHeight() : (float)mImageView.getWidth() / mBitmap.getWidth());

                mStartX = (mImageView.getWidth() - mIvScaleX * mBitmap.getWidth())/2;
                mStartY = (mImageView.getHeight() -  mIvScaleY * mBitmap.getHeight())/2;

                yolo = true;

                Thread thread = new Thread(MainActivity.this);
                thread.start();
            }
        });

        mEppButtonDetect = findViewById(R.id.detectEppButton);
        mEppProgressBar = (ProgressBar) findViewById(R.id.eppProgressBar);
        mEppButtonDetect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                mEppButtonDetect.setEnabled(false);
                mButtonDetect.setEnabled(false);
                mEppProgressBar.setVisibility(ProgressBar.VISIBLE);
                mEppButtonDetect.setText(getString(R.string.run_model));

                mImgScaleX = (float)mBitmap.getWidth() / PrePostProcessor.mInputWidth;
                mImgScaleY = (float)mBitmap.getHeight() / PrePostProcessor.mInputHeight;

                mIvScaleX = (mBitmap.getWidth() > mBitmap.getHeight() ? (float)mImageView.getWidth() / mBitmap.getWidth() : (float)mImageView.getHeight() / mBitmap.getHeight());
                mIvScaleY  = (mBitmap.getHeight() > mBitmap.getWidth() ? (float)mImageView.getHeight() / mBitmap.getHeight() : (float)mImageView.getWidth() / mBitmap.getWidth());

                mStartX = (mImageView.getWidth() - mIvScaleX * mBitmap.getWidth())/2;
                mStartY = (mImageView.getHeight() -  mIvScaleY * mBitmap.getHeight())/2;

                yolo = false;

                Thread thread = new Thread(MainActivity.this);
                thread.start();
            }
        });

        try {
//            mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), "yolov5x.torchscript.ptl"));
            mModule = LiteModuleLoader.load(MainActivity.assetFilePath(getApplicationContext(), "yolov5snulls.torchscript.ptl"));
            BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open("classes.txt")));
            String line;
            List<String> classes = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                classes.add(line);
            }
            PrePostProcessor.mClasses = new String[classes.size()];
            classes.toArray(PrePostProcessor.mClasses);
        } catch (IOException e) {
            Log.e("Object Detection", "Error reading assets", e);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_CANCELED) {
            switch (requestCode) {
                case 0:
                    if (resultCode == RESULT_OK && data != null) {
                        mBitmap = (Bitmap) data.getExtras().get("data");
                        Matrix matrix = new Matrix();
                        matrix.postRotate(90.0f);
                        mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                        mImageView.setImageBitmap(mBitmap);
                    }
                    break;
                case 1:
                    if (resultCode == RESULT_OK && data != null) {
                        Uri selectedImage = data.getData();
                        String[] filePathColumn = {MediaStore.Images.Media.DATA};
                        if (selectedImage != null) {
                            Cursor cursor = getContentResolver().query(selectedImage,
                                    filePathColumn, null, null, null);
                            if (cursor != null) {
                                cursor.moveToFirst();
                                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                                String picturePath = cursor.getString(columnIndex);
                                mBitmap = BitmapFactory.decodeFile(picturePath);
                                Matrix matrix = new Matrix();
                                matrix.postRotate(90.0f);
                                mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                                mImageView.setImageBitmap(mBitmap);
                                cursor.close();
                            }
                        }
                    }
                    break;
            }
        }
    }

    @Override
    public void run() {
//        Bitmap resizedBitmap = Bitmap.createScaledBitmap(mBitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true);
        Pair<Integer, Integer> newShape = new Pair<>(PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight);
        Triple<Integer, Integer, Integer> color = new Triple<>(114, 114, 114);
        Bitmap resizedBitmap = this.letterbox(mBitmap, newShape, color, true, true, true, 32);
        final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB);
        IValue[] outputTuple = mModule.forward(IValue.from(inputTensor)).toTuple();
        final Tensor outputTensor = outputTuple[0].toTensor();
        final float[] outputs = outputTensor.getDataAsFloatArray();
        final ArrayList<Result> results =  PrePostProcessor.outputsToNMSPredictions(outputs, mImgScaleX, mImgScaleY, mIvScaleX, mIvScaleY, mStartX, mStartY);

        if(yolo) {
            runOnUiThread(() -> {
                mButtonDetect.setEnabled(true);
                mEppButtonDetect.setEnabled(true);
                mButtonDetect.setText(getString(R.string.detect));
                mProgressBar.setVisibility(ProgressBar.INVISIBLE);
                mResultView.setResults(results);
                mResultView.invalidate();
                mResultView.setVisibility(View.VISIBLE);
                mEppResultView.setVisibility(View.INVISIBLE);
            });
        } else {
            AlgoritmoDecision algo = new AlgoritmoDecision(results, false);
            final ArrayList<EPPResult> eppResults = algo.getResultadosProcesados();
            runOnUiThread(() -> {
                mButtonDetect.setEnabled(true);
                mEppButtonDetect.setEnabled(true);
                mEppButtonDetect.setText(getString(R.string.detect_epp));
                mEppProgressBar.setVisibility(ProgressBar.INVISIBLE);
                mEppResultView.setResults(eppResults);
                mEppResultView.invalidate();
                mResultView.setVisibility(View.INVISIBLE);
                mEppResultView.setVisibility(View.VISIBLE);
            });
        }

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

        // only scale image???no padding,just return scale image
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
