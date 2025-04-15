package com.example.lottieanimation;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.airbnb.lottie.LottieAnimationView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "LottieAnimation";
    private LottieAnimationView animationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        animationView = findViewById(R.id.animationView);
        animationView.setAnimation("animation.json");
        animationView.playAnimation();
        
        // Add failure listener to debug issues
        animationView.setFailureListener(exception -> {
            Log.e(TAG, "Animation failed to load: " + exception.getMessage());
            Toast.makeText(this, "Animation failed to load: " + exception.getMessage(), Toast.LENGTH_LONG).show();
        });
        
        // Log animation status
        Log.d(TAG, "Animation setup complete. File: animation.json, AutoPlay: true, Loop: true");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (animationView != null && !animationView.isAnimating()) {
            animationView.playAnimation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (animationView != null && animationView.isAnimating()) {
            animationView.pauseAnimation();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (animationView != null) {
            animationView.cancelAnimation();
        }
    }
}