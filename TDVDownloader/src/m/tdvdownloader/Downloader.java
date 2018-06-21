package m.tdvdownloader;

import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.text.TextUtils;
import android.util.Base64;

import com.mob.MobSDK;
import com.mob.tools.MobHandlerThread;
import com.mob.tools.network.HttpConnection;
import com.mob.tools.network.HttpResponseCallback;
import com.mob.tools.network.NetworkHelper;
import com.mob.tools.network.NetworkHelper.NetworkTimeOut;
import com.mob.tools.utils.ReflectHelper;
import com.mob.tools.utils.SharePrefrenceHelper;
import com.mob.tools.utils.UIHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import cn.sharesdk.framework.Platform;
import cn.sharesdk.framework.PlatformActionListener;
import cn.sharesdk.framework.ShareSDK;
import cn.sharesdk.tumblr.Tumblr;

public class Downloader implements PlatformActionListener, Callback {
	private DownloadWatcher watcher;
	private SharePrefrenceHelper sp;
	private String lastId;
	private boolean firstPage;
	private String url;
	private HashMap<String, Object> values;
	private HashMap<String, Integer> sizeHistory;
	private HashMap<Integer, ArrayList<String>> sizeToFileName;
	private HashMap<String, byte[]> sampleHistory;
	private int offset;
	private NetworkTimeOut timeout;
	private ArrayList<String> downloadList;
	private Handler handler;
	private Platform tumblr;
	private HashSet<String> videoUrls;
	
	public void filterDashboard(DownloadWatcher watcher) {
		this.watcher = watcher;
		firstPage = true;
		timeout = new NetworkTimeOut();
		timeout.connectionTimeout = 5000;
		timeout.readTimout = 30000;
		downloadList = new ArrayList<String>();
		handler = MobHandlerThread.newHandler(this);
		videoUrls = new HashSet<String>();
		
		new Thread() {
			public void run() {
				sp = new SharePrefrenceHelper(MobSDK.getContext());
				sp.open("TDVD");
				lastId = sp.getString("last_id");
				readHistory();
				UIHandler.sendEmptyMessage(0, new Callback() {
					public boolean handleMessage(Message message) {
						tumblr = ShareSDK.getPlatform(Tumblr.NAME);
						tumblr.setPlatformActionListener(Downloader.this);
						url = "https://api.tumblr.com/v2/user/dashboard";
						values = new HashMap<String, Object>();
						values.put("type", "video");
						tumblr.customerProtocol(url, "GET", (short) 1, values, null);
						return false;
					}
				});
			}
		}.start();
	}
	
	private void readHistory() {
		sizeHistory = new HashMap<String, Integer>();
		sampleHistory = new HashMap<String, byte[]>();
		sizeToFileName = new HashMap<Integer, ArrayList<String>>();
		File historyFile = new File(MobSDK.getContext().getFilesDir(), ".history");
		if (historyFile.exists()) {
			try {
				FileInputStream fis = new FileInputStream(historyFile);
				DataInputStream dis = new DataInputStream(fis);
				for (int i = 0, size = dis.readInt(); i < size; i++) {
					String fileName = dis.readUTF();
					int fileSize = dis.readInt();
					int count = dis.readInt();
					byte[] sample = new byte[count];
					dis.readFully(sample);
					sizeHistory.put(fileName, fileSize);
					sampleHistory.put(fileName, sample);
					ArrayList<String> fileNames = sizeToFileName.get(fileSize);
					if (fileNames == null) {
						fileNames = new ArrayList<String>();
						sizeToFileName.put(fileSize, fileNames);
					}
					fileNames.add(fileName);
				}
				dis.close();
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public void onComplete(final Platform platform, int action, HashMap<String, Object> hashMap) {
		boolean noNews = false;
		HashMap<String, Object> response = (HashMap<String, Object>) hashMap.get("response");
		ArrayList<HashMap<String, Object>> posts = (ArrayList<HashMap<String,Object>>) response.get("posts");
		ArrayList<String> urls = new ArrayList<String>();
		for (HashMap<String, Object> post : posts) {
			if (!noNews) {
				String id = String.valueOf(post.get("id"));
				if (id.equals(lastId)) {
					noNews = true;
				} else {
					if (firstPage) {
						lastId = id;
						firstPage = false;
					}
					String videoUrl = (String) post.get("video_url");
					urls.add(videoUrl);
					if (watcher != null) {
						watcher.onNewUrl(videoUrl);
					}
				}
			}
		}
		Message msg = new Message();
		msg.arg1 = noNews ? 1 : 0;
		msg.obj = urls;
		handler.sendMessage(msg);
	}
	
	public void onError(Platform platform, int i, Throwable throwable) {
		throwable.printStackTrace();
		onCancel(platform, i);
	}
	
	public void onCancel(Platform platform, int i) {
		Message msg = new Message();
		msg.arg1 = 1;
		handler.sendMessage(msg);
	}
	
	@SuppressWarnings("unchecked")
	public boolean handleMessage(Message message) {
		if (message.obj != null) {
			videoUrls.addAll((ArrayList<String>) message.obj);
		}
	
		if (message.arg1 == 1 || videoUrls.size() >= 200) {
			try {
				final ArrayList<String> urls = new ArrayList<String>(videoUrls);
				new SimpleCoworkThread<Integer>(10) {
					private int index = -1;
					
					protected Integer pickWork() throws Throwable {
						index++;
						return index < urls.size() ? index : null;
					}
					
					protected void work(Integer index) throws Throwable {
						String url = urls.get(index);
						checkUrl(url);
					}
				}.start();
			} catch (Throwable t) {
				t.printStackTrace();
			}
			if (watcher != null) {
				watcher.onComplete(downloadList.size());
			}
		} else {
			UIHandler.sendEmptyMessage(0, new Callback() {
				public boolean handleMessage(Message message) {
					offset += 20;
					values.put("offset", offset);
					tumblr.setPlatformActionListener(Downloader.this);
					tumblr.customerProtocol(url, "GET", (short) 1, values, null);
					return false;
				}
			});
		}
		return false;
	}
	
	private void checkUrl(final String url) {
		try {
			final Uri uri = Uri.parse(url);
			File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
			File tumblrDir = new File(downloadDir, "tumblr");
			final File downloadTo = new File(tumblrDir, uri.getPath());
			final String fileName = downloadTo.getName();
			synchronized (sizeHistory) {
				if (sizeHistory.containsKey(fileName)) {
					return;
				}
			}
			
			new NetworkHelper().rawGet(url, new HttpResponseCallback() {
				public void onResponse(HttpConnection conn) throws Throwable {
					List<String> list = conn.getHeaderFields().get("Content-Length");
					if (list != null && list.size() > 0) {
						int fileSize = Integer.parseInt(list.get(0));
						InputStream is = conn.getInputStream();
						DataInputStream dis = new DataInputStream(is);
						byte[] buf = new byte[Math.min(fileSize, 64 * 1024)];
						dis.readFully(buf);
						dis.close();
						byte[] sample = new byte[64];
						int step = buf.length / sample.length;
						for (int i = 0; i < sample.length; i++) {
							sample[i] = buf[i * step];
						}
						
						synchronized (sizeHistory) {
							ArrayList<String> fileNames = sizeToFileName.get(fileSize);
							if (fileNames == null) {
								fileNames = new ArrayList<String>();
								sizeToFileName.put(fileSize, fileNames);
							} else {
								for (String fn : fileNames) {
									byte[] fileSample = sampleHistory.get(fn);
									if (arrayEquals(sample, fileSample)) {
										return;
									}
								}
							}
							
							fileNames.add(fileName);
							sizeHistory.put(fileName, fileSize);
							sampleHistory.put(fileName, sample);
							downloadList.add(url);
							if (watcher != null) {
								watcher.onCheckUrl(url);
							}
						}
					}
				}
			}, timeout);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}
	
	private boolean arrayEquals(byte[] left, byte[] right) {
		if (left.length == right.length) {
			for (int i = 0; i < left.length; i++) {
				if (left[i] != right[i]) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	
	public void download() {
		if (downloadList.size() > 0) {
			for (String url : downloadList) {
				postDownloadTask(url);
			}
		}
		sp.putString("last_id", lastId);
		saveHistory();
	}
	
	private void postDownloadTask(String url) {
		Uri uri = Uri.parse(url);
		File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		File tumblrDir = new File(downloadDir, "tumblr");
		File downloadTo = new File(tumblrDir, uri.getPath());
		if (!downloadTo.getParentFile().exists()) {
			downloadTo.getParentFile().mkdirs();
		}
		Request request = new Request(uri);
		request.setAllowedNetworkTypes(Request.NETWORK_WIFI);
		request.setDestinationUri(Uri.fromFile(downloadTo));
		request.setNotificationVisibility(Request.VISIBILITY_HIDDEN);
		DownloadManager dm = (DownloadManager) MobSDK.getContext().getSystemService(Context.DOWNLOAD_SERVICE);
		dm.enqueue(request);
	}
	
	private void saveHistory() {
		try {
			File tmpFile = new File(MobSDK.getContext().getFilesDir(), ".history.tmp");
			FileOutputStream fos = new FileOutputStream(tmpFile);
			DataOutputStream dos = new DataOutputStream(fos);
			dos.writeInt(sizeHistory.size());
			for (Entry<String, Integer> e : sizeHistory.entrySet()) {
				dos.writeUTF(e.getKey());
				dos.writeInt(e.getValue());
				byte[] sample = sampleHistory.get(e.getKey());
				dos.writeInt(sample.length);
				dos.write(sample);
			}
			dos.flush();
			dos.close();
			
			File historyFile = new File(MobSDK.getContext().getFilesDir(), ".history");
			if (historyFile.exists()) {
				historyFile.delete();
			}
			tmpFile.renameTo(historyFile);
		} catch (Throwable t) {}
		
		if (watcher != null) {
			watcher.onHistorySaved();
		}
	}
	
	public interface DownloadWatcher {
		public void onNewUrl(String url);
		
		public void onCheckUrl(String url);
		
		public void onComplete(int urlCount);
		
		public void onHistorySaved();
	}
	
}
