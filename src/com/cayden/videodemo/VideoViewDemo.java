package com.cayden.videodemo;
//Download by http://www.codefans.net
import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;

public class VideoViewDemo extends Activity {
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);  

		//String url = "http://carey-blog-image.googlecode.com/files/vid_20120510_090204.mp4";
		String url = "http://192.168.0.106/video/Kara-Guilty.mp4";
		//System.currentTimeMillis() 
		String filepath=Environment.getExternalStorageDirectory().getAbsolutePath()
				+ "/VideoCache/" + Constants.PLAYPATH + ".mp4";
		File file=new File(filepath);
		if(file.isFile()&&file.exists()){
			file.delete();
		}
		Intent intent = new Intent();
		intent.setClass(VideoViewDemo.this, BBVideoPlayer.class);
		intent.putExtra("url", url);
		intent.putExtra("cache",filepath
				);
		startActivity(intent);
	}
}
