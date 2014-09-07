package eu.siacs.conversations.utils;

import android.graphics.BitmapFactory;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jingle.JingleFile;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
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
public class ImageDownloader extends Thread {

	private XmppConnectionService xmppConnectionService;
	Message message;
	JingleFile file;

	public ImageDownloader(XmppConnectionService service, Message message) {
		this.message = message;
		this.xmppConnectionService = service;

		message.setType(Message.TYPE_IMAGE);
		message.setStatus(Message.STATUS_RECEIVING);
	}

	@Override
	public void run() {
		try {
			this.file = xmppConnectionService.getFileBackend().getJingleFile(message);
			handleImagePreview();

			updateMessageBody();

			this.xmppConnectionService.markMessage(this.message,
					Message.STATUS_RECEIVED);

		} catch(Exception ex) {
			ex.printStackTrace();
			message.setType(Message.TYPE_TEXT);

			this.xmppConnectionService.markMessage(this.message,
					Message.STATUS_RECEIVED);
		}
	}

	private void updateMessageBody() {
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(this.file.getAbsolutePath(), options);
		int imageHeight = options.outHeight;
		int imageWidth = options.outWidth;
		message.setBody(Long.toString(file.getSize()) + ',' + imageWidth + ','
				+ imageHeight + ',' + message.getBody());

	}

	private void handleImagePreview() throws IOException {
		// Do the http request
		DefaultHttpClient client = new DefaultHttpClient();
		HttpRequestBase request = new HttpGet(this.message.getBody());
		HttpResponse res = client.execute(request);

		// Download the file
		InputStream input = new BufferedInputStream(res.getEntity().getContent());
		OutputStream output = new FileOutputStream(this.file);

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
