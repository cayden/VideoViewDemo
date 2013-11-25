package com.cayden.videodemo;
//Download by http://www.codefans.net
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.webkit.URLUtil;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;
import com.cayden.videodemo.R;
/**
 * 
 * TODO  视频缓存的核心
 * @author cuiran
 * @version 1.0.0
 */
public class BBVideoPlayer extends Activity {

	private static final String TAG="BBVideoPlayer";
	private VideoView mVideoView;
	private TextView tvcache;
	private String remoteUrl;
	private String localUrl;
	private ProgressDialog progressDialog = null;
	private Thread receiveThread=null;
	/**
	 * 定义了初始缓存区的大小，当视频加载到初始缓存区满的时候，播放器开始播放，
	 */
	private static final int READY_BUFF = 2000 * 1024;
	/**
	 * 核心交换缓存区，主要是用来动态调节缓存区，当网络环境较好的时候，该缓存区为初始大小，
	 * 当网络环境差的时候，该缓存区会动态增加，主要就是为了避免视频播放的时候出现一卡一卡的现象。
	 */
	private static final int CACHE_BUFF = 500 * 1024;

	private boolean isready = false;
	private boolean iserror = false;
	private int errorCnt = 0;
	private int curPosition = 0;
	private long mediaLength = 0;
	private long readSize = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.bbvideoplayer);

		findViews();
		init();
		playvideo();
	}

	private void findViews() {
		this.mVideoView = (VideoView) findViewById(R.id.bbvideoview);
		this.tvcache = (TextView) findViewById(R.id.tvcache);
	}

	private void init() {
		Intent intent = getIntent();

		this.remoteUrl = intent.getStringExtra("url");
		System.out.println("remoteUrl: " + remoteUrl);

		if (this.remoteUrl == null) {
			finish();
			return;
		}

		this.localUrl = intent.getStringExtra("cache");

		mVideoView.setMediaController(new MediaController(this));

		mVideoView.setOnPreparedListener(new OnPreparedListener() {

			public void onPrepared(MediaPlayer mediaplayer) {
				Log.i(TAG, "onPrepared");
				dismissProgressDialog();
				mVideoView.seekTo(curPosition);
				mediaplayer.start();
			}
		});

		mVideoView.setOnCompletionListener(new OnCompletionListener() {

			public void onCompletion(MediaPlayer mediaplayer) {
				Log.i(TAG, "onCompletion");
				curPosition = 0;
				mVideoView.pause();
			}
		});

		mVideoView.setOnErrorListener(new OnErrorListener() {

			public boolean onError(MediaPlayer mediaplayer, int i, int j) {
				Log.i(TAG, "onError");
				iserror = true;
				errorCnt++;
				mVideoView.pause();
				showProgressDialog();
				return true;
			}
		});
	}

	private void showProgressDialog() {
		mHandler.post(new Runnable() {

			@Override
			public void run() {
				if (progressDialog == null) {
					progressDialog = ProgressDialog.show(BBVideoPlayer.this,
							"视频缓存", "正在努力加载中 ...", true, false);
				}
			}
		});
	}

	private void dismissProgressDialog() {
		mHandler.post(new Runnable() {

			@Override
			public void run() {
				if (progressDialog != null) {
					progressDialog.dismiss();
					progressDialog = null;
				}
			}
		});
	}

	private void playvideo() {
		if (!URLUtil.isNetworkUrl(this.remoteUrl)) {
			mVideoView.setVideoPath(this.remoteUrl);
			mVideoView.start();
			return;
		}

		showProgressDialog();

		receiveThread=new Thread(new Runnable() {

			@Override
			public void run() {
				FileOutputStream out = null;
				InputStream is = null;

				try {
					URL url = new URL(remoteUrl);
					HttpURLConnection httpConnection = (HttpURLConnection) url
							.openConnection();

					if (localUrl == null) {
						localUrl = Environment.getExternalStorageDirectory()
								.getAbsolutePath()
								+ "/VideoCache/"
								+ System.currentTimeMillis() + ".mp4";
					}

					System.out.println("localUrl: " + localUrl);

					File cacheFile = new File(localUrl);

					if (!cacheFile.exists()) {
						cacheFile.getParentFile().mkdirs();
						cacheFile.createNewFile();
					}

					readSize = cacheFile.length();
					out = new FileOutputStream(cacheFile, true);

					httpConnection.setRequestProperty("User-Agent", "NetFox");
					httpConnection.setRequestProperty("RANGE", "bytes="
							+ readSize + "-");

					is = httpConnection.getInputStream();

					mediaLength = httpConnection.getContentLength();
					if (mediaLength == -1) {
						return;
					}

					mediaLength += readSize;

					byte buf[] = new byte[4 * 1024];
					int size = 0;
					long lastReadSize = 0;

					mHandler.sendEmptyMessage(VIDEO_STATE_UPDATE);

					while ((size = is.read(buf)) != -1) {
						try {
							out.write(buf, 0, size);
							readSize += size;
						} catch (Exception e) {
							e.printStackTrace();
						}

						if (!isready) {
							if ((readSize - lastReadSize) > READY_BUFF) {
								lastReadSize = readSize;
								 Log.i(TAG, "readSize="+readSize+"-READY_BUFF="+READY_BUFF);
								mHandler.sendEmptyMessage(CACHE_VIDEO_READY);
							}
						} else {
							if ((readSize - lastReadSize) > CACHE_BUFF
									* (errorCnt + 1)) {
								lastReadSize = readSize;
								 Log.i(TAG, "readSize="+readSize+"-READY_BUFF="+ CACHE_BUFF
											* (errorCnt + 1));
								mHandler.sendEmptyMessage(CACHE_VIDEO_UPDATE);
							}
						}
					}

					mHandler.sendEmptyMessage(CACHE_VIDEO_END);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					if (out != null) {
						try {
							out.close();
						} catch (IOException e) {
							//
						}
					}

					if (is != null) {
						try {
							is.close();
						} catch (IOException e) {
							//
						}
					}
				}

			}
		});
		receiveThread.start();
	}

	private final static int VIDEO_STATE_UPDATE = 0;
	/**
	 * 缓存准备
	 */
	private final static int CACHE_VIDEO_READY = 1;
	/**
	 * 缓存修改
	 */
	private final static int CACHE_VIDEO_UPDATE = 2;
	/**
	 * 缓存结束
	 */
	private final static int CACHE_VIDEO_END = 3;

	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case VIDEO_STATE_UPDATE:
				Log.i(TAG, "更新显示");
				double cachepercent = readSize * 100.00 / mediaLength * 1.0;
				String s = String.format("已缓存: [%.2f%%]", cachepercent);
				if (mVideoView.isPlaying()) {
					curPosition = mVideoView.getCurrentPosition();
					int duration = mVideoView.getDuration();
					duration = duration == 0 ? 1 : duration;

					double playpercent = curPosition * 100.00 / duration * 1.0;

					int i = curPosition / 1000;
					int hour = i / (60 * 60);
					int minute = i / 60 % 60;
					int second = i % 60;

					s += String.format(" 播放: %02d:%02d:%02d [%.2f%%]", hour,
							minute, second, playpercent);
				}

				tvcache.setText(s);

				mHandler.sendEmptyMessageDelayed(VIDEO_STATE_UPDATE, 1000);
//				mHandler.sendEmptyMessage(VIDEO_STATE_UPDATE);
				
				break;

			case CACHE_VIDEO_READY:
				Log.i(TAG, "缓存准备");
				isready = true;
				mVideoView.setVideoPath(localUrl);
				mVideoView.start();
				break;

			case CACHE_VIDEO_UPDATE:
				Log.i(TAG, "缓存修改");
				if (iserror) {
					mVideoView.setVideoPath(localUrl);
					mVideoView.start();
					iserror = false;
				}
				break;

			case CACHE_VIDEO_END:
				Log.i(TAG, "缓存结束");
				if (iserror) {
					mVideoView.setVideoPath(localUrl);
					mVideoView.start();
					iserror = false;
				}
				break;
			}

			super.handleMessage(msg);
		}
	};

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		
		super.onDestroy();
	}
	
	
}
