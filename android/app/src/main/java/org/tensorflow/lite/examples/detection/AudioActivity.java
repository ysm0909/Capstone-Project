package org.tensorflow.lite.examples.detection;

import static android.speech.tts.TextToSpeech.ERROR;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.examples.detection.R;
import java.util.Locale;

public class AudioActivity extends AppCompatActivity {
    // 화면 실시간으로 보이게 하고, 촬영 버튼 있는 부분으로, DetectorActivity 부분 갖다써야됨
/*
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_readyset);

    btn_click = (ImageButton) findViewById(R.id.btn_click); // Readyset 촬영 불투명 배경
        btn_click.setOnClickListener(view -> {
        Intent intent = new Intent(getApplicationContext(), DetectorActivity.class);
        startActivity(intent); // 화면 이동
    });

*/

    private TextToSpeech tts;  // TTS 변수 선언
    private ImageButton btn_audio, btn_home; // 미리 정의해두기

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio);

        btn_audio = (ImageButton)findViewById (R.id.btn_audio);
        btn_home = (ImageButton)findViewById (R.id.btn_home);

        // TTS 관리하는 부분 시작 ********
        // TTS를 생성하고 OnInitListener로 초기화 한다.
        tts = new TextToSpeech (this, new TextToSpeech.OnInitListener () {
            @Override
            public void onInit(int status) {
                if (status != ERROR) {
                    // 언어를 선택한다.
                    tts.setLanguage (Locale.KOREAN);
                    tts.setPitch (1.0f);
                }
            }
        });

        // Intent로부터 데이터 받기
        String title = getIntent().getStringExtra("title");

        // Intent로 content 데이터도 받고,
        // String content = getIntent().getStringExtra("content");
        // String text에 넣으면 될듯?
        String text1 = String.format("이 음료는 %s입니다. ", title);

        // TTS 버튼 클릭 리스너 설정
        // ImageButton btn_audio = findViewById(R.id.btn_audio);
        btn_audio.setOnClickListener(v -> {
            // String text = title != null ? title : getResources().getString(R.string.error_msg);
            String text = title != null ? text1 :getResources().getString(R.string.error_msg);
            tts.setSpeechRate(1.0f);
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        });
        // TTS 관리하는 부분 끝 ********

        btn_home.setOnClickListener(view -> {
            Intent intent = new Intent(AudioActivity.this, HomeActivity.class);
            startActivity(intent); // HomeActivity로 이동
            finish(); // 현재 Activity를 종료
        });
    }

    // 새로 추가한 onDestroy 함수
    @Override
    public void onDestroy() {
        super.onDestroy ();
        // TTS 객체가 남아있다면 실행을 중지하고 메모리에서 제거한다.
        if (tts != null) {
            tts.stop ();
            tts.shutdown ();
            tts = null;
        }
    }
    //
}
