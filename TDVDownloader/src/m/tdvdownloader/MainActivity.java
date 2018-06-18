package m.tdvdownloader;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

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
				tv.setText(urls.get(i));
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
		runOnUiThread(new Runnable() {
			public void run() {
				urls.add(url);
				adapter.notifyDataSetChanged();
			}
		});
	}
	
	public void onNewPage() {
		onNewUrl("===============");
	}
	
	public void onComplete() {
		runOnUiThread(new Runnable() {
			public void run() {
				finish();
			}
		});
	}
	
	protected void onDestroy() {
		super.onDestroy();
		downloader.download();
		System.exit(0);
	}
	
}
