package com.example.test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.example.test.ApplyingFFT;
//import com.example.audio_tone_detection.MainActivity.Looper;
import com.example.test.R;

import android.support.v7.app.ActionBarActivity;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity {

	private final static int MICROPHONE = MediaRecorder.AudioSource.MIC;
	static final String TAG = "Audio Tone Detector";
	private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
	private static final String AUDIO_RECORDER_FOLDER = "AudioRecorder";
	private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";
	private static final int RECORDER_BPP = 16;

	// private Looper samplingThread;
	private static int audioSourceId = MICROPHONE;
	private static String wndFuncName;

	private static int fftLen = 2048;
	private static int sampleRate = 44100;
	private final static int BYTE_OF_SAMPLE = 2;
	boolean isRecording;
	private Thread recordingThread = null;
	private int bufferSize = 0;

	public static final int test_signal_1_freq1 = 0x7f0a001c;
	public static final int test_signal_2_db1 = 0x7f0a001f;
	AudioRecord record;
	private int minBufferSize;
	private byte audioData[];
	int mPeakPos = 0;
	public double frequency = 0.0;
	double mMaxFFTSample;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		final TextView tv = (TextView) findViewById(R.id.frequency_value);
		Button play = (Button) findViewById(R.id.play_Tone);

		// To avoid initialisation failure , incase of a bufferSizze lesser than
		// this below computed min buffer size value.

		minBufferSize = AudioRecord.getMinBufferSize(sampleRate,
				AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

		bufferSize = minBufferSize * 3;

		audioData = new byte[bufferSize];

	}

	public void startRecording(View view) {

		// TODO Auto-generated method stub

		record = new AudioRecord(audioSourceId, sampleRate,
				AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
				bufferSize);

		if (record.getState() == AudioRecord.STATE_UNINITIALIZED) {
			Log.e(TAG, "Looper::run(): Fail to initialize AudioRecord()");
			// If failed somehow, leave user a chance to change preference.
			return;

		}

		record.startRecording();
		isRecording = true;
		long startTime = System.currentTimeMillis();
		recordingThread = new Thread(new Runnable() {
			public void run() {
				while (!Thread.interrupted()) {
					writeAudioDataToFile();
				}
			}
		}, "AudioRecorder Thread");

		recordingThread.start();

		startTime = System.currentTimeMillis();

		while (System.currentTimeMillis() - startTime <= 5000L) {

			Log.i(TAG, "Recording thread running");

		}

		recordingThread.interrupt();
		stopRecording(view);

	}

	public void writeAudioDataToFile() {

		double absNormalizedSignal[];
		String filename = getTempFilename();
		FileOutputStream os = null;

		int read = 0;

		while (isRecording) {
			read = record.read(audioData, 0, bufferSize);

			if (read > 0) {
				absNormalizedSignal = calculateFFT(audioData);

			}
		}
	}

	private void copyWaveFile(String inFileName, String outFileName) {

		FileInputStream in = null;
		FileOutputStream out = null;
		long totalAudioLen = 0;
		long totalDataLen = totalAudioLen + 36;
		long longSampleRate = sampleRate;
		int channels = 2;
		long byteRate = RECORDER_BPP * sampleRate * channels / 8;

		byte[] data = new byte[bufferSize];

		try {
			in = new FileInputStream(inFileName);
			out = new FileOutputStream(outFileName);
			totalAudioLen = in.getChannel().size();
			totalDataLen = totalAudioLen + 36;

			Log.i(TAG, "File size:" + totalDataLen);
			while (in.read(data) != -1) {
				out.write(data);
			}

			in.close();
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private String getTempFilename() {
		String filepath = Environment.getExternalStorageDirectory().getPath();
		File file = new File(filepath, AUDIO_RECORDER_FOLDER);

		if (!file.exists()) {
			file.mkdirs();
		}

		File tempFile = new File(filepath, AUDIO_RECORDER_TEMP_FILE);

		if (tempFile.exists())
			tempFile.delete();

		return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
	}

	private String getFilename() {
		String filepath = Environment.getExternalStorageDirectory().getPath();
		File file = new File(filepath, AUDIO_RECORDER_FOLDER);

		if (!file.exists()) {
			file.mkdirs();
		}

		return (file.getAbsolutePath() + "/" + System.currentTimeMillis() + AUDIO_RECORDER_FILE_EXT_WAV);
	}

	public void stopRecording(View v) {

		// Compute frequency from calculated peak set to mMaxFFTSample

		frequency = (sampleRate * mMaxFFTSample) / 2048;
		System.out.println("Frequency detected " + frequency);
		Log.i(TAG, "Frequency of recorded tone " + frequency);

		// stop Recording Activity
		if (null != record) {
			isRecording = false;
			record.stop();
			record.release();
			record = null;
			recordingThread = null;
			updateTextView();
		}

	}

	public double[] calculateFFT(byte[] signal) {
		final int mNumberOfFFTPoints = 2048;
		int fftSize = bufferSize / 2;

		double temp;
		Complex[] y;
		Complex[] complexSignal = new Complex[mNumberOfFFTPoints];
		double[] magnitude = new double[mNumberOfFFTPoints / 2];

		for (int i = 0; i < mNumberOfFFTPoints; i++) {
			temp = (double) ((signal[2 * i] & 0xFF) | (signal[2 * i + 1] << 8)) / 32768.0F;
			complexSignal[i] = new Complex(temp, 0.0);
		}

		y = FFT.fft(complexSignal); // --> Here I use FFT class

		mMaxFFTSample = 0.0;

		for (int i = 0; i < (mNumberOfFFTPoints / 2); i++) {
			magnitude[i] = Math.sqrt(Math.pow(y[i].re(), 2)
					+ Math.pow(y[i].im(), 2));
			if (magnitude[i] > mMaxFFTSample) {
				mMaxFFTSample = i;
				mPeakPos = i;
			}
		}

		return magnitude;

	}

	public void updateTextView() {

		TextView textView = (TextView) findViewById(R.id.frequency_value);
		textView.setText(" Frequency of recorded tone " + frequency);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}


	public void onResume() {
		super.onResume();
	}
}
