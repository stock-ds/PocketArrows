package com.beatsportable.beats;

import java.io.File;
import java.util.HashMap;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * Lightweight metadata for the song browser: banners, note counts, NPS, and high score.
 * Results are cached by path + lastModified so scrolling the list stays cheap.
 */
public class SongSelectInfo {

	public String bannerPath;
	public String statsText;
	public int noteCount;
	public float nps;
	public int difficultyMeter;
	public int highScore;
	public int songCount;

	private static final HashMap<String, SongSelectInfo> cache = new HashMap<String, SongSelectInfo>();
	private static final HashMap<String, Bitmap> bannerCache = new HashMap<String, Bitmap>();
	private static final int BANNER_W = 144;
	private static final int BANNER_H = 56;

	private static final String[] PACK_BANNER_NAMES = {
		"banner.png", "banner.jpg", "bn.png", "bn.jpg",
		"jacket.png", "jacket.jpg", "pack.png", "pack.jpg"
	};

	public static SongSelectInfo forItem(File f, boolean isDir) {
		if (f == null) return null;
		String key = f.getAbsolutePath() + "|" + f.lastModified() + "|" + (isDir ? "d" : "f");
		SongSelectInfo cached = cache.get(key);
		if (cached != null) return cached;

		SongSelectInfo info = new SongSelectInfo();
		try {
			if (isDir) {
				fillDirectory(f, info);
			} else if (Tools.isStepfile(f.getPath())) {
				fillStepfile(f, info);
			}
		} catch (Exception e) {
			// Listing must never crash the browser
		}
		cache.put(key, info);
		return info;
	}

	public Bitmap getBannerBitmap() {
		if (bannerPath == null) return null;
		Bitmap b = bannerCache.get(bannerPath);
		if (b != null && !b.isRecycled()) return b;
		try {
			BitmapFactory.Options bounds = new BitmapFactory.Options();
			bounds.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(bannerPath, bounds);
			int sample = 1;
			if (bounds.outWidth > BANNER_W * 2 || bounds.outHeight > BANNER_H * 2) {
				int w = bounds.outWidth;
				int h = bounds.outHeight;
				while (w / sample > BANNER_W * 2 && h / sample > BANNER_H * 2) {
					sample *= 2;
				}
			}
			BitmapFactory.Options opts = new BitmapFactory.Options();
			opts.inSampleSize = sample;
			opts.inPreferredConfig = Bitmap.Config.RGB_565;
			b = BitmapFactory.decodeFile(bannerPath, opts);
			if (b != null) {
				bannerCache.put(bannerPath, b);
			}
			return b;
		} catch (Exception e) {
			return null;
		}
	}

	private static void fillDirectory(File dir, SongSelectInfo info) throws Exception {
		File[] files = dir.listFiles();
		if (files == null) return;

		String stepfile = Tools.checkStepfileDir(dir);
		if (stepfile != null) {
			fillStepfile(new File(stepfile), info); // throws Exception, caught by forItem
			if (info.bannerPath == null) {
				info.bannerPath = findImageIn(files, true);
			}
			return;
		}

		int songs = 0;
		for (File child : files) {
			if (child.isDirectory() && !child.getName().startsWith(".")) {
				if (Tools.checkStepfileDir(child) != null) {
					songs++;
				}
			}
		}
		info.songCount = songs;
		info.bannerPath = findPackBanner(dir, files);
		if (songs > 0) {
			info.statsText = String.format(Tools.getString(R.string.MenuFilechooser_pack_songs), songs);
		}
	}

	private static void fillStepfile(File stepfile, SongSelectInfo info) throws Exception {
		DataParser dp = new DataParser(stepfile.getAbsolutePath());
		File banner = dp.df.getBanner();
		if (banner != null && banner.exists()) {
			info.bannerPath = banner.getAbsolutePath();
		} else {
			File parent = stepfile.getParentFile();
			if (parent != null) {
				info.bannerPath = findImageIn(parent.listFiles(), true);
			}
		}

		int defaultDiff = 2;
		try {
			defaultDiff = Integer.parseInt(Tools.getSetting(R.string.difficultyLevel, R.string.difficultyLevelDefault));
		} catch (Exception e) {}

		DataNotesData chosen = null;
		int chosenDiff = -1;
		for (DataNotesData nd : dp.df.notesData) {
			int d = nd.getDifficulty().ordinal();
			if (chosen == null || (d <= defaultDiff && d >= chosenDiff) || (chosenDiff > defaultDiff && d < chosenDiff)) {
				chosen = nd;
				chosenDiff = d;
			}
		}
		if (chosen == null) return;

		boolean dwi = Tools.isDWIFile(stepfile.getName());
		info.noteCount = countNotes(chosen.getNotesData(), dwi);
		info.difficultyMeter = chosen.getDifficultyMeter();
		float durationSec = estimateDurationSec(dp.df, chosen, dwi);
		info.nps = (durationSec > 0.5f) ? (info.noteCount / durationSec) : 0f;

		String md5 = dp.df.md5hash + chosen.getDifficultyMeter();
		info.highScore = new Scoreboard(md5).getScore();

		if (info.highScore > 0) {
			info.statsText = String.format(
					Tools.getString(R.string.MenuFilechooser_stats_score),
					info.noteCount, info.nps, info.difficultyMeter, info.highScore);
		} else {
			info.statsText = String.format(
					Tools.getString(R.string.MenuFilechooser_stats),
					info.noteCount, info.nps, info.difficultyMeter);
		}
	}

	private static int countNotes(String notesData, boolean dwi) {
		if (notesData == null) return 0;
		if (dwi) return countDwiNotes(notesData);
		int count = 0;
		String[] lines = notesData.split("\n");
		for (int li = 0; li < lines.length; li++) {
			String line = lines[li].trim();
			if (line.length() == 0 || line.charAt(0) == '/' || line.charAt(0) == ',') continue;
			for (int i = 0; i < line.length(); i++) {
				char c = line.charAt(i);
				if (c == '1' || c == '2' || c == '4') count++;
			}
		}
		return count;
	}

	private static int countDwiNotes(String notes) {
		int count = 0;
		for (int i = 0; i < notes.length(); i++) {
			char c = notes.charAt(i);
			switch (c) {
				case '2': case '4': case '6': case '8':
					count++; break;
				case '1': case '3': case '7': case '9': case 'A': case 'B':
					count += 2; break;
				default: break;
			}
		}
		return count;
	}

	private static float estimateDurationSec(DataFile df, DataNotesData nd, boolean dwi) {
		String notes = nd.getNotesData();
		if (notes == null || notes.length() == 0) return 0f;
		float bpm = 120f;
		try {
			bpm = df.getBPM(0f);
			if (bpm <= 1f) bpm = 120f;
		} catch (Exception e) {}
		float duration;
		if (dwi) {
			duration = estimateDwiDurationSec(notes, bpm);
		} else {
			int measures = 0;
			String[] lines = notes.split("\n");
			boolean sawRow = false;
			for (int li = 0; li < lines.length; li++) {
				String line = lines[li].trim();
				if (line.length() == 0 || line.charAt(0) == '/') continue;
				if (line.charAt(0) == ',') {
					measures++;
					sawRow = false;
				} else {
					sawRow = true;
				}
			}
			if (sawRow) measures++;
			if (measures < 1) measures = 1;
			duration = measures * 4f * 60f / bpm;
		}
		return duration;
	}

	private static float estimateDwiDurationSec(String notes, float bpm) {
		float beatUnit = 1f / 8f;
		float beats = 0f;
		for (int i = 0; i < notes.length(); i++) {
			char c = notes.charAt(i);
			switch (c) {
				case '(': beatUnit = 1f / 16f; break;
				case '[': beatUnit = 1f / 24f; break;
				case '{': beatUnit = 1f / 64f; break;
				case '`': beatUnit = 1f / 192f; break;
				case ')': case ']': case '}': case '\'':
					beatUnit = 1f / 8f; break;
				case '!': case '/': case '\n': case '\r': case ' ':
					break;
				default:
					if ((c >= '0' && c <= '9') || c == 'A' || c == 'B') {
						beats += beatUnit;
					}
					break;
			}
		}
		return beats * 60f / bpm;
	}

	private static String findPackBanner(File dir, File[] files) {
		if (files == null) return null;
		for (String name : PACK_BANNER_NAMES) {
			File f = new File(dir, name);
			if (f.isFile()) return f.getAbsolutePath();
		}
		return findImageIn(files, true);
	}

	private static String findImageIn(File[] files, boolean preferBannerName) {
		if (files == null) return null;
		File fallback = null;
		for (File f : files) {
			if (f == null || !f.isFile()) continue;
			String n = f.getName().toLowerCase();
			if (!(n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp"))) {
				continue;
			}
			if (preferBannerName && (n.contains("bn") || n.contains("banner") || n.contains("jacket"))) {
				return f.getAbsolutePath();
			}
			if (fallback == null) fallback = f;
		}
		return fallback != null ? fallback.getAbsolutePath() : null;
	}
}
