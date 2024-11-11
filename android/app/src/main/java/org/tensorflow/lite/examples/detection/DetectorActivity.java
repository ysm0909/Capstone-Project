/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import static android.speech.tts.TextToSpeech.ERROR; // 얘가 들어감 이거는 텍스트를 음성으로 변환하는 데 사용

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas; //그래픽 처리를 위해 사용
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint; // 그래픽 처리를 위해 사용
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech; // 추가한 TTS 관련 라이브러리
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;
import android.content.Intent;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale; // 얘를 새로 넣었음. TTS에서 한국어를 설정하기 위한 지역 라이브러리

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.DetectorFactory;
import org.tensorflow.lite.examples.detection.tflite.YoloV5Classifier;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f; // 탐지를 신뢰할 수 있는 최소 신뢰도 (일반 권장값으로 보통 0.4가 사용됨. 수정 가능)
    private static final boolean MAINTAIN_ASPECT = true;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(480, 480); // 우리가 표시할 사이즈로,480*480 기본값은 680*680이었음. // 이 값이 높을 수록 해상도 높아짐 ***********
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private YoloV5Classifier detector; // YOLOv5 객체 탐지 모델을 나타내는 변수

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null; // Bitmap객체
    private Bitmap croppedBitmap = null; // Bitmap객체
    private Bitmap cropCopyBitmap = null; // Bitmap객체 // 카메라에서 가져온 이미지를 저장하고, 모델이 처리할 이미지를 생성

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform; // Matrix 객체 // 이미지 변환 및 좌표 변환을 처리
    private Matrix cropToFrameTransform; // Matrix 객체

    private MultiBoxTracker tracker; // 탐지된 객체를 추적하는 데 사용

    private BorderedText borderedText;

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        // 카메라 미리보기의 크기가 선택되었을 때 호출
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx); // borderedText: 경계선을 포함한 텍스트를 그리는 데 사용
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        final int modelIndex = modelView.getCheckedItemPosition();
        final String modelString = modelStrings.get(modelIndex);

        try { // 탐지 모델 초기화 : DetectorFactory.getDetector를 통해 YOLOv5 모델을 로드
            detector = DetectorFactory.getDetector(getAssets(), modelString);
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        int cropSize = detector.getInputSize();

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        // 이미지 변환 행렬 설정: 카메라 이미지와 탐지 모델의 입력 이미지 크기를 맞추기 위해 Matrix 객체를 설정
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    protected void updateActiveModel() {
        // Get UI information before delegating to background
        final int modelIndex = modelView.getCheckedItemPosition();
        final int deviceIndex = deviceView.getCheckedItemPosition();
        String threads = threadsTextView.getText().toString().trim();
        final int numThreads = Integer.parseInt(threads);

        handler.post(() -> {
            if (modelIndex == currentModel && deviceIndex == currentDevice
                    && numThreads == currentNumThreads) {
                return;
            }
            currentModel = modelIndex;
            currentDevice = deviceIndex;
            currentNumThreads = numThreads;

            // Disable classifier while updating
            if (detector != null) {
                detector.close();
                detector = null;
            }

            // Lookup names of parameters.
            String modelString = modelStrings.get(modelIndex);
            String device = deviceStrings.get(deviceIndex);

            LOGGER.i("Changing model to " + modelString + " device " + device);

            // Try to load model.

            try {
                detector = DetectorFactory.getDetector(getAssets(), modelString);
                // Customize the interpreter to the type of device we want to use.
                if (detector == null) {
                    return;
                }
            }
            catch(IOException e) {
                e.printStackTrace();
                LOGGER.e(e, "Exception in updateActiveModel()");
                Toast toast =
                        Toast.makeText(
                                getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
                toast.show();
                finish();
            }


            if (device.equals("CPU")) {
                detector.useCPU();
            } else if (device.equals("GPU")) {
                detector.useGpu();
            } else if (device.equals("NNAPI")) {
                detector.useNNAPI();
            }
            detector.setNumThreads(numThreads);

            int cropSize = detector.getInputSize();
            croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

            frameToCropTransform =
                    ImageUtils.getTransformationMatrix(
                            previewWidth, previewHeight,
                            cropSize, cropSize,
                            sensorOrientation, MAINTAIN_ASPECT);

            cropToFrameTransform = new Matrix();
            frameToCropTransform.invert(cropToFrameTransform);
        });
    }

    protected TextToSpeech tts; // 이 코드 추가함 (TTS를 위해 추가한 라인)
    @Override
    protected void processImage() {
        // 이미지 처리: 카메라에서 가져온 이미지를 모델이 처리할 수 있도록 준비
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        // TTS 동작 부분에 대한 코드
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != ERROR) { // 초기화가 성공적으로 완료된 경우에만 다음 작업을 수행하도록 설정
                    tts.setLanguage(Locale.KOREAN); // 텍스트 음성 변환의 언어를 한국어로 설정 (TTS가 한국어 텍스트를 음성으로 변환 가능)
                }
            }
        });


        // runInBackground: 이미지를 백그라운드에서 탐지 모델에 전달하여 객체를 인식
        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis();
                        // 여기 사이에 아래 문장을 넣었음
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        //

                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                        // detector.recognizeImage: YOLOv5 모델을 사용하여 객체를 인식
                        lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                        Log.e("CHECK", "run: " + results.size());

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Style.STROKE);
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }


                        //여기부터 아래까지를 주석으로 만들었음 (지웠음)
                        /*
                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, paint);

                                cropToFrameTransform.mapRect(location);

                                result.setLocation(location);
                                mappedRecognitions.add(result);
                            }
                        }
                        */ // 윗 부분이 원본 (전체 다 객체 인식하는 것)
                        //// 중간에 있는 오브젝트만 인식하도록 코드를 수정했음.
                        // 아래 코드로 수정함.
                        final List<Classifier.Recognition> mappedRecognitions = new LinkedList<Classifier.Recognition>();

                        int[] centerIndex = {0, 0};
                        float[] centerSize = {0, 0};
                        float centerX = 0;
                        float centerY = 0;
                        int r = 0;
                        for (Classifier.Recognition result : results) {
                            RectF location = result.getLocation();
                            centerX = location.centerX();
                            centerY = location.centerY();
                            float size = location.height() * location.width();

                            float diff = Math.abs(centerX - centerY);
                            if (diff < centerSize[1] || centerSize[1] == 0) {
                                centerSize[0] = size;
                                centerSize[1] = diff;
                                centerIndex[0] = r;
                            }
                            r++;
                        } // 여기가 중간 값을 가져오는 부분임

                        if (results.size() > 0) {
                            // 이 아래 1개 문장 원래 주석처리 돼있었음.
                            mappedRecognitions.clear ();
                            // 이 위 문장 이하동문
// mappedRecognitions 부분을 사용 한다면 인식 결과를 초기화 하게 되는데, 이건 직접 실행 시켜 보고 주석 해제할 지 말지 결정 해야 될 듯
                            Classifier.Recognition result = results.get(centerIndex[0]);
                            RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                canvas.drawRect(location, paint);
                                cropToFrameTransform.mapRect(location);
                                result.setLocation(location);
                                mappedRecognitions.add(result);
                                // tts.setSpeechRate(1.0f); // tts 속도 5배 증가 // 여기랑 이 아래 코드 tts 주석처리 해두면 소리는 안남. ************************************************************
                                // tts.speak(result.getTitle(),TextToSpeech.QUEUE_FLUSH,null,null);
                                // 이 아래 2개 문장 원래 주석처리 돼있었음.
                                //mappedRecognitions.clear(); // 결과 리스트 초기화
                                mappedRecognitions.add(result);
                                // 이 위 2 문장 이하동문

                                // AudioActivity로 결과를 전달 // 아래 3줄 추가함 ****************
                                Intent intent = new Intent(DetectorActivity.this, AudioActivity.class);
                                intent.putExtra("title", result.getTitle());
                                startActivity(intent); // 바로 실행되는 건데, 버튼 누르면 실행되게 하려면 어떻게 하는지 아직 모르겠음. 찾아봐야됨.
                            }
                        }
                        // 결과 처리: 탐지된 객체를 화면에 그리거나 음성으로 출력
                        //////////////
                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                        runOnUiThread(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        showFrameInfo(previewWidth + "x" + previewHeight);
                                        showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                        showInference(lastProcessingTimeMs + "ms");
                                    }
                                });
                    }
                });
    }

    @Override
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }
}
