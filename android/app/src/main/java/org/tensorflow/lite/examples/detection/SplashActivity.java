package org.tensorflow.lite.examples.detection;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.examples.detection.R;
import java.util.Locale;

public class SplashActivity extends AppCompatActivity {

    private TextToSpeech mTTS;

    @Override
    protected void onCreate(Bundle savedInstanceStare) {
        super.onCreate(savedInstanceStare);
        setContentView(R.layout.activity_splash); // 레이아웃 적용 !!!!!!!!!

        mTTS = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = mTTS.setLanguage(Locale.KOREAN);
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "Language not supported");
                    }
                } else {
                    Log.e("TTS", "Initialization failed");
                }
            }
        });

        Handler handler = new Handler ();
        handler.postDelayed(new Runnable() {

            private void speak(String text) {
                mTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null);
            }
            @Override
            public void run() {
                Intent intent = new Intent(getApplicationContext(),HomeActivity.class);
                startActivity(intent);
                mTTS.setSpeechRate(1.0f);
                speak("안녕하세요   터치톡 입니다,   음료를  확인하면  자동으로  음성  안내  동작으로  넘어가고 ,  좌측  하단의  버튼으로  음성  안내를  받으실  수  있습니다.  또한  우측  하단의  버튼으로  처음으로  돌아가실  수  있습니다.   중앙의  시작하기  버튼을  눌러주세요");
                finish();
            }
        },3000); // 3초 후 HomeActivity로 이동
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();
    }
}
