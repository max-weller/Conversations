package eu.siacs.conversations.utils;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Downloadable;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.persistance.FileBackend;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jingle.JingleFile;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.*;

/**
 * Once an image url is received, the ImageDownloader will download it to
 * display a preview to the user
 *
 * @author Max Weller
 * @version 2014-09-07-001
 */
public class ImageDownloader implements Downloadable {

	private XmppConnectionService xmppConnectionService;
	Message message;
	File tempFile;

	public ImageDownloader(XmppConnectionService service, Message message) {
		this.message = message;
		this.xmppConnectionService = service;

		message.setType(Message.TYPE_IMAGE);
		new Thread(new Runnable() {
			@Override
			public void run() {
				threadCheck();
			}
		}).start();
	}

	public void start() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				threadDownload();
			}
		}).start();
	}

	private void threadCheck() {
		try {
			Log.d(Config.LOGTAG, "ImageDownloader, checking file size...");
			this.xmppConnectionService.markMessage(this.message,
					Message.STATUS_RECEIVING);

			SharedPreferences prefs = xmppConnectionService.getPreferences();
			int allowedImageSize = Integer.valueOf(prefs.getString("auto_accept_file_size", "524288"), 10);
			int imageSize = this.retrieveFileSize();

			if (imageSize > allowedImageSize)  {
				this.xmppConnectionService.markMessage(this.message,
						Message.STATUS_RECEIVED_OFFER);
			} else {
				this.threadDownload();
			}

		} catch(Exception ex) {
			ex.printStackTrace();
			message.setType(Message.TYPE_TEXT);

			this.xmppConnectionService.markMessage(this.message,
					Message.STATUS_RECEIVED);
		}
	}

	private void threadDownload() {
		try {
			Log.d(Config.LOGTAG, "ImageDownloader, downloading...");
			this.xmppConnectionService.markMessage(this.message,
					Message.STATUS_RECEIVING);

			File outputDir = xmppConnectionService.getCacheDir();
			this.tempFile = File.createTempFile("download", "jpg", outputDir);

			handleImagePreview();

			FileBackend backend = xmppConnectionService.getFileBackend();
			Uri uri = Uri.parse("file://" + tempFile.getAbsolutePath());
			Log.d(Config.LOGTAG, "ImageDownloader temp file uri: "+uri.toString());
			backend.copyImageToPrivateStorage(message, uri);

			this.xmppConnectionService.markMessage(this.message,
					Message.STATUS_RECEIVED);

		} catch(Exception ex) {
			ex.printStackTrace();
			message.setType(Message.TYPE_TEXT);

			this.xmppConnectionService.markMessage(this.message,
					Message.STATUS_RECEIVED);
		}
	}

	private int retrieveFileSize() throws IOException {
		// Do the http request
		DefaultHttpClient client = new DefaultHttpClient();
		HttpRequestBase request = new HttpHead(this.message.getBody());
		HttpResponse res = client.execute(request);
		Header contLen = res.getFirstHeader("Content-Length");
		if (contLen == null) return Integer.MAX_VALUE;
		int value = Integer.parseInt(contLen.getValue(), 10);
		return value;
	}

	private void handleImagePreview() throws IOException {
		// Do the http request
		DefaultHttpClient client = new DefaultHttpClient();
		HttpRequestBase request = new HttpGet(this.message.getBody());

		HttpResponse res = client.execute(request);

		// Download the file
		InputStream input = new BufferedInputStream(res.getEntity().getContent());
		OutputStream output = new FileOutputStream(this.tempFile);

		byte data[] = new byte[1024];

		long total = 0; int count;
		while ((count = input.read(data)) != -1) {
			total += count;
			// publishing the progress....
			//publishProgress((int)(total*100/lenghtOfFile));
			output.write(data, 0, count);
		}

		output.flush();
		output.close();
		input.close();
	}


}
