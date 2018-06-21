package m.tdvdownloader;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler.Callback;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.mob.tools.utils.UIHandler;

import java.util.ArrayList;

import m.tdvdownloader.Downloader.DownloadWatcher;

public class MainActivity extends Activity implements DownloadWatcher {
	private Downloader downloader;
	private ArrayList<String> urls;
	private BaseAdapter adapter;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		urls = new ArrayList<String>();
		final ListView lv = new ListView(this);
		lv.setCacheColorHint(0);
		lv.setSelector(new ColorDrawable(0));
		lv.setDividerHeight(0);
		setContentView(lv);
		adapter = new BaseAdapter() {
			public int getCount() {
				return urls.size();
			}

			public Object getItem(int i) {
				return i;
			}

			public long getItemId(int i) {
				return i;
			}

			public View getView(int i, View view, ViewGroup viewGroup) {
				if (view == null) {
					view = new TextView(viewGroup.getContext());
				}

				TextView tv = (TextView) view;
				String data = urls.get(i);
				if (data.startsWith("+ ")) {
					tv.setTextColor(0xffff0000);
				} else {
					tv.setTextColor(0xffffffff);
				}
				tv.setText(data.substring(2));
				return view;
			}

			public void notifyDataSetChanged() {
				super.notifyDataSetChanged();
				lv.setSelection(urls.size() - 1);
			}
		};
		lv.setAdapter(adapter);

		downloader = new Downloader();
		downloader.filterDashboard(this);
	}
	
	public void onNewUrl(final String url) {
		UIHandler.sendEmptyMessage(0, new Callback() {
			public boolean handleMessage(Message message) {
				urls.add("- " + url);
				adapter.notifyDataSetChanged();
				return false;
			}
		});
	}
	
	public void onCheckUrl(final String url) {
		UIHandler.sendEmptyMessage(0, new Callback() {
			public boolean handleMessage(Message message) {
				urls.add("+ " + url);
				adapter.notifyDataSetChanged();
				return false;
			}
		});
	}
	
	public void onComplete(final int urlCount) {
		UIHandler.sendEmptyMessage(0, new Callback() {
			public boolean handleMessage(Message message) {
				Toast.makeText(MainActivity.this, "url count: " + urlCount, Toast.LENGTH_SHORT).show();
				finish();
				return false;
			}
		});
	}
	
	protected void onDestroy() {
		super.onDestroy();
		downloader.download();
	}
	
	public void onHistorySaved() {
		System.exit(0);
	}
	
}
