package org.tensorflow.lite.examples.detection;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {
    private ImageButton btn_start;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // DetectorActivity.java 페이지로 이동
        btn_start = (ImageButton) findViewById(R.id.btn_start); ///////////////////////////////////////
        btn_start.setOnClickListener(view -> {
            Intent intent = new Intent(getApplicationContext(), DetectorActivity.class);
            startActivity(intent); // 화면 이동
        });

        /*
        btn_start = (ImageButton) findViewById(R.id.btn_start);
        btn_start.setOnClickListener(view -> {
            Intent intent = new Intent(getApplicationContext(), DetectorActivity.class);
            startActivity(intent); // 화면 이동
        });
         */
    }
}
