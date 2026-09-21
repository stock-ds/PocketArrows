package com.beatsportable.beats;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.VideoView;

public class MenuSplash extends Activity {

	private boolean finished;
	private VideoView video;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.splash);

		finished = false;
		video = (VideoView) findViewById(R.id.intro_video);
		ImageView fallback = (ImageView) findViewById(R.id.intro_fallback);
		TextView skip = (TextView) findViewById(R.id.intro_skip);

		View.OnClickListener goHome = new View.OnClickListener() {
			public void onClick(View v) {
				finishSplash();
			}
		};
		skip.setOnClickListener(goHome);
		fallback.setOnClickListener(goHome);
		playFromCache(fallback);
	}

	private void playFromCache(final ImageView fallback) {
		try {
			File cache = new File(getCacheDir(), "intro.mp4");
			if (!cache.exists() || cache.length() < 1000) {
				InputStream in = getAssets().open("intro.mp4");
				FileOutputStream out = new FileOutputStream(cache);
				byte[] buf = new byte[8192];
				int n;
				while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
				in.close();
				out.close();
			}
			video.setVisibility(View.VISIBLE);
			fallback.setVisibility(View.GONE);
			video.setVideoPath(cache.getAbsolutePath());
			video.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
				public void onCompletion(MediaPlayer mp) {
					finishSplash();
				}
			});
			video.setOnErrorListener(new MediaPlayer.OnErrorListener() {
				public boolean onError(MediaPlayer mp, int what, int extra) {
					showFallbackThenContinue(fallback);
					return true;
				}
			});
			video.start();
			video.postDelayed(new Runnable() {
				public void run() {
					finishSplash();
				}
			}, 500);
		} catch (Exception e) {
			showFallbackThenContinue(fallback);
		}
	}

	private void showFallbackThenContinue(ImageView fallback) {
		if (video != null) video.setVisibility(View.GONE);
		fallback.setVisibility(View.VISIBLE);
		fallback.postDelayed(new Runnable() {
			public void run() {
				finishSplash();
			}
		}, 500);
	}

	private void finishSplash() {
		if (finished) return;
		finished = true;
		if (video != null) {
			try { video.stopPlayback(); } catch (Exception ignored) {}
		}
		startActivity(new Intent(this, MenuHome.class));
		finish();
	}

	@Override
	public void onBackPressed() {
		finishSplash();
	}
}
