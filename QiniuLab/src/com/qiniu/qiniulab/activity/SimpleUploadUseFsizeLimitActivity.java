package com.qiniu.qiniulab.activity;

import java.io.File;

import org.json.JSONException;
import org.json.JSONObject;

import com.ipaulpro.afilechooser.utils.FileUtils;
import com.qiniu.android.http.CompletionHandler;
import com.qiniu.android.http.HttpManager;
import com.qiniu.android.http.ResponseInfo;
import com.qiniu.android.storage.UpCompletionHandler;
import com.qiniu.android.storage.UpProgressHandler;
import com.qiniu.android.storage.UploadManager;
import com.qiniu.android.storage.UploadOptions;
import com.qiniu.qiniulab.R;
import com.qiniu.qiniulab.config.QiniuLabConfig;
import com.qiniu.qiniulab.utils.Tools;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class SimpleUploadUseFsizeLimitActivity extends ActionBarActivity {

	private SimpleUploadUseFsizeLimitActivity context;
	private EditText uploadFileKeyEditText;
	private TextView uploadTokenTextView;
	private TextView uploadFileTextView;
	private TextView uploadLogTextView;
	private TextView uploadFsizeLimitTextView;
	private LinearLayout uploadStatusLayout;
	private ProgressBar uploadProgressBar;
	private TextView uploadSpeedTextView;
	private TextView uploadFileLengthTextView;
	private TextView uploadPercentageTextView;
	private HttpManager httpManager;
	private UploadManager uploadManager;
	private static final int REQUEST_CODE = 8090;
	private long uploadLastTimePoint;
	private long uploadLastPos;
	private long uploadFileLength;

	public SimpleUploadUseFsizeLimitActivity() {
		this.context = this;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.simple_upload_use_fsizelimit_activity);
		this.uploadTokenTextView = (TextView) this
				.findViewById(R.id.simple_upload_use_fsizelimit_upload_token);
		this.uploadFileTextView = (TextView) this
				.findViewById(R.id.simple_upload_use_fsizelimit_upload_file);
		this.uploadFileKeyEditText = (EditText) this
				.findViewById(R.id.simple_upload_use_fsizelimit_file_key);
		this.uploadFsizeLimitTextView = (TextView) this
				.findViewById(R.id.simple_upload_use_fsizelimit_textview);
		this.uploadProgressBar = (ProgressBar) this
				.findViewById(R.id.simple_upload_use_fsizelimit_upload_progressbar);
		this.uploadProgressBar.setMax(100);
		this.uploadStatusLayout = (LinearLayout) this
				.findViewById(R.id.simple_upload_use_fsizelimit_status_layout);
		this.uploadSpeedTextView = (TextView) this
				.findViewById(R.id.simple_upload_use_fsizelimit_upload_speed_textview);
		this.uploadFileLengthTextView = (TextView) this
				.findViewById(R.id.simple_upload_use_fsizelimit_upload_file_length_textview);
		this.uploadPercentageTextView = (TextView) this
				.findViewById(R.id.simple_upload_use_fsizelimit_upload_percentage_textview);
		this.uploadStatusLayout.setVisibility(LinearLayout.INVISIBLE);
		this.uploadLogTextView = (TextView) this
				.findViewById(R.id.simple_upload_use_fsizelimit_log_textview);

	}

	public void getUploadToken(View view) {
		if (this.httpManager == null) {
			this.httpManager = new HttpManager();
		}
		this.httpManager.postData(QiniuLabConfig.makeUrl(
				QiniuLabConfig.REMOTE_SERVICE_SERVER,
				QiniuLabConfig.SIMPLE_UPLOAD_USE_FSIZE_LIMIT_PATH),
				QiniuLabConfig.EMPTY_BODY, null, null, new CompletionHandler() {

					@Override
					public void complete(ResponseInfo respInfo,
							JSONObject jsonData) {
						if (respInfo.statusCode == 200) {
							try {
								String uploadToken = jsonData
										.getString("uptoken");
								int fsizeLimit = jsonData.getInt("fsizeLimit");
								uploadTokenTextView.setText(uploadToken);
								uploadFsizeLimitTextView.setText("<="
										+ fsizeLimit + " Byte ("
										+ Tools.formatSize(fsizeLimit) + ")");
							} catch (JSONException e) {
								Toast.makeText(
										context,
										context.getString(R.string.qiniu_get_upload_token_failed),
										Toast.LENGTH_LONG).show();
							}
						} else {
							Toast.makeText(
									context,
									context.getString(R.string.qiniu_get_upload_token_failed),
									Toast.LENGTH_LONG).show();
						}
					}
				});
	}

	public void selectUploadFile(View view) {
		Intent target = FileUtils.createGetContentIntent();
		Intent intent = Intent.createChooser(target,
				this.getString(R.string.choose_file));
		try {
			this.startActivityForResult(intent, REQUEST_CODE);
		} catch (ActivityNotFoundException ex) {
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_CODE:
			// If the file selection was successful
			if (resultCode == RESULT_OK) {
				if (data != null) {
					// Get the URI of the selected file
					final Uri uri = data.getData();
					try {
						// Get the file path from the URI
						final String path = FileUtils.getPath(this, uri);
						this.uploadFileTextView.setText(path);
					} catch (Exception e) {
						Toast.makeText(
								context,
								context.getString(R.string.qiniu_get_upload_file_failed),
								Toast.LENGTH_LONG).show();
					}
				}
			}
			break;
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	public void uploadFile(View view) {
		if (this.uploadManager == null) {
			this.uploadManager = new UploadManager();
		}
		String uploadToken = this.uploadTokenTextView.getText().toString();
		File uploadFile = new File(this.uploadFileTextView.getText().toString());
		String uploadFileKey = this.uploadFileKeyEditText.getText().toString();
		UploadOptions uploadOptions = new UploadOptions(null, null, false,
				new UpProgressHandler() {

					@Override
					public void progress(String key, double percent) {
						int percentage = (int) (percent * 100);
						uploadProgressBar.setProgress(percentage);
						uploadPercentageTextView.setText(percentage + " %");
						long uploadCurrentPos = (long) (uploadFileLength * percent);
						long uploadCurrentMillis = System.currentTimeMillis();
						long uploadSliceSize = uploadCurrentPos - uploadLastPos;
						long uploadSliceMillis = uploadCurrentMillis
								- uploadLastTimePoint;

						if (uploadSliceMillis != 0) {
							uploadSpeedTextView
									.setText((uploadSliceSize / uploadSliceMillis)
											+ " KB/s");
						}
						// update pos
						uploadLastTimePoint = uploadCurrentMillis;
						uploadLastPos = uploadCurrentPos;
					}

				}, null);
		final long startTime = System.currentTimeMillis();
		final long fileLength = uploadFile.length();
		this.uploadFileLength = fileLength;
		this.uploadLastTimePoint = startTime;
		this.uploadLastPos = 0;
		// prepare status
		uploadPercentageTextView.setText("0 %");
		uploadSpeedTextView.setText("0 KB/s");
		uploadFileLengthTextView.setText(Tools.formatSize(fileLength));
		uploadStatusLayout.setVisibility(LinearLayout.VISIBLE);

		this.uploadManager.put(uploadFile, uploadFileKey, uploadToken,
				new UpCompletionHandler() {
					@Override
					public void complete(String key, ResponseInfo respInfo,
							JSONObject jsonData) {
						// reset status
						uploadStatusLayout
								.setVisibility(LinearLayout.INVISIBLE);
						uploadProgressBar.setProgress(0);
						long lastMillis = System.currentTimeMillis()
								- startTime;
						if (respInfo.isOK()) {
							try {
								String fileKey = jsonData.getString("key");
								String fileHash = jsonData.getString("hash");
								uploadLogTextView.append("File Size: "
										+ Tools.formatSize(uploadFileLength)
										+ "\r\n");
								uploadLogTextView.append("File Key: " + fileKey
										+ "\r\n");
								uploadLogTextView.append("File Hash: "
										+ fileHash + "\r\n");
								uploadLogTextView.append("Last Time: "
										+ Tools.formatMilliSeconds(lastMillis)
										+ "\r\n");
								uploadLogTextView.append("Average Speed: "
										+ (fileLength / lastMillis)
										+ " KB/s\r\n");
								uploadLogTextView.append("StatusCode: "
										+ respInfo.statusCode + "\r\n");
								uploadLogTextView.append("Reqid: "
										+ respInfo.reqId + "\r\n");
								uploadLogTextView
										.append("---------------------------\r\n");

							} catch (JSONException e) {
								Toast.makeText(
										context,
										context.getString(R.string.qiniu_upload_file_response_parse_error),
										Toast.LENGTH_LONG).show();
								uploadLogTextView.append(jsonData.toString());
								uploadLogTextView.append("\r\n");
								uploadLogTextView
										.append("---------------------------\r\n");
							}
						} else {
							Toast.makeText(
									context,
									context.getString(R.string.qiniu_upload_file_failed),
									Toast.LENGTH_LONG).show();

							uploadLogTextView.append("StatusCode: "
									+ respInfo.statusCode + "\r\n");
							uploadLogTextView.append("Reqid: " + respInfo.reqId
									+ "\r\n");
							uploadLogTextView.append("Xlog: " + respInfo.xlog
									+ "\r\n");
							uploadLogTextView.append("Error: " + respInfo.error
									+ "\r\n");
							uploadLogTextView
									.append("---------------------------\r\n");
						}
					}

				}, uploadOptions);
	}
}
