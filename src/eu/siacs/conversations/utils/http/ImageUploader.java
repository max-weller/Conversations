package eu.siacs.conversations.utils.http;

import android.os.Environment;
import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jingle.JingleFile;
import eu.siacs.conversations.xmpp.stanzas.MessagePacket;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

/**
 * Created 13.09.14 21:33.
 *
 * @author Max Weller
 * @version 2014-09-13-001
 */
public class ImageUploader {

	private XmppConnectionService xmppConnectionService;
	private Message message;
	private JingleFile file;

	public ImageUploader(XmppConnectionService service) {
		xmppConnectionService = service;
	}

	public void init(Message message) {
		this.message = message;
		this.file = xmppConnectionService.getFileBackend().getJingleFile(message);
		new Thread(new Runnable() {
			@Override
			public void run() {
				uploadThread();
			}
		}).start();

	}

	private void uploadThread() {
		final HttpUploader twajax = new HttpUploader();
		twajax.addPostFile(new HttpUploader.PostFile("file", "image.webp", "image/webp", file));
		twajax.postData(Config.HTTP_UPLOAD_ENDPOINT+"/api/upload/file", new Runnable() {
			@Override
			public void run() {
				try {
					Log.d(Config.LOGTAG, "ImageUploader http status: "+ String.valueOf(twajax.getHttpCode()) +", result: "+twajax.getResult(), twajax.getError());
					JSONObject result = (JSONObject)twajax.getJsonResult();
					String hash = result.getString("hash");
					String url = Config.HTTP_UPLOAD_ENDPOINT + "/" + hash + ".webp";

					Account account = message.getConversation().getAccount();
					MessagePacket packet = xmppConnectionService.getMessageGenerator().generateChat(message);
					packet.setBody(url);
					if (!account.getXmppConnection().getFeatures().sm()
							&& message.getConversation().getMode() != Conversation.MODE_MULTI) {
						xmppConnectionService.markMessage(message, Message.STATUS_SEND);
					}
					xmppConnectionService.sendMessagePacket(account, packet);

				} catch(Exception ex) {
					ex.printStackTrace();
					xmppConnectionService.markMessage(message, Message.STATUS_SEND_FAILED);
				}
			}
		});

	}


}
