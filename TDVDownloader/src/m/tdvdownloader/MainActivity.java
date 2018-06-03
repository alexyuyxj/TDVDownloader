package m.tdvdownloader;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;

import com.mob.MobSDK;
import com.mob.tools.MobHandlerThread;
import com.mob.tools.utils.SharePrefrenceHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import cn.sharesdk.framework.Platform;
import cn.sharesdk.framework.PlatformActionListener;
import cn.sharesdk.framework.ShareSDK;
import cn.sharesdk.tumblr.Tumblr;

public class MainActivity extends Activity implements Callback, PlatformActionListener{
	private SharePrefrenceHelper sp;
	private String lastId;
	private boolean firstPage;
	private Handler handler;
	private Platform tumblr;
	private String url;
	private HashMap<String, Object> values;
	private int offset;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		sp = new SharePrefrenceHelper(MobSDK.getContext());
		sp.open("TDVD");
		lastId = sp.getString("last_id");
		firstPage = true;
		handler = MobHandlerThread.newHandler(this);
		
		tumblr = ShareSDK.getPlatform(Tumblr.NAME);
		tumblr.setPlatformActionListener(this);
		url = "https://api.tumblr.com/v2/user/dashboard";
		values = new HashMap<String, Object>();
		values.put("type", "video");
		tumblr.customerProtocol(url, "GET", (short) 1, values, null);
	}
	
	public boolean handleMessage(Message message) {
		ArrayList<String[]> urls = (ArrayList<String[]>) message.obj;
		boolean noNews = false;
		String sinceId = null;
		for (String[] url : urls) {
			if (!noNews) {
				String id = url[0];
				if (id.equals(lastId)) {
					noNews = true;
				} else {
					if (firstPage) {
//						sp.putString("last_id", id);
						firstPage = false;
					}
					String videoId = url[1];
					sinceId = sinceId == null ? id : sinceId;
					download(videoId);
				}
			}
		}
		
		if (!noNews) {
			offset += urls.size();
			values.put("offset", offset);
			tumblr.setPlatformActionListener(MainActivity.this);
			tumblr.customerProtocol(url, "GET", (short) 1, values, null);
		}
		return false;
	}
	
	public void onComplete(Platform platform, int action, HashMap<String, Object> hashMap) {
		HashMap<String, Object> response = (HashMap<String, Object>) hashMap.get("response");
		ArrayList<HashMap<String, Object>> posts = (ArrayList<HashMap<String,Object>>) response.get("posts");
		ArrayList<String[]> urls = new ArrayList<String[]>();
		for (HashMap<String, Object> post : posts) {
			String id = String.valueOf(post.get("id"));
			String videoUrl = (String) post.get("video_url");
			urls.add(new String[] {id, videoUrl});
		}
		Message msg = new Message();
		msg.obj = urls;
		handler.sendMessage(msg);
	}
	
	public void onError(Platform platform, int i, Throwable throwable) {
		throwable.printStackTrace();
	}
	
	public void onCancel(Platform platform, int i) {
	
	}
	
	boolean ss;
	private void download(String url) {
		if (ss) {
			return;
		}
		
		ss = true;
		Uri uri = Uri.parse(url);
		Request request = new Request(uri);
		request.setAllowedNetworkTypes(Request.NETWORK_WIFI);
		File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "tumblr");
		File downloadTo = new File(dir, uri.getPath());
		if (!downloadTo.getParentFile().exists()) {
			downloadTo.getParentFile().mkdirs();
		}
		request.setDestinationUri(Uri.fromFile(downloadTo));
		DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
		dm.enqueue(request);
	}
	
	protected void onDestroy() {
		super.onDestroy();
		System.exit(0);
	}
}
