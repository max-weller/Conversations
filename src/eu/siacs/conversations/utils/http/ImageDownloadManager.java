package eu.siacs.conversations.utils.http;

import android.content.SharedPreferences;
import android.util.Log;
import eu.siacs.conversations.Config;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.xmpp.jingle.JingleConnection;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages ImageDownloader instances
 *
 * @author Max Weller
 * @version 2014-09-13-001
 */
public class ImageDownloadManager {

	private XmppConnectionService mXmppConnectionService;

	private List<ImageDownloader> connections = new CopyOnWriteArrayList<ImageDownloader>();

	public ImageDownloadManager(XmppConnectionService service) {
		this.mXmppConnectionService = service;
	}

	public ImageDownloader createNewConnection(Message message) {
		ImageDownloader downloader = new ImageDownloader(mXmppConnectionService);
		downloader.init(message);
		this.connections.add(downloader);
		return downloader;
	}

	public void finishConnection(ImageDownloader connection) {
		this.connections.remove(connection);
	}

	public boolean shouldDisplayImagePreview(Message message) {
		SharedPreferences sharedPref = mXmppConnectionService.getPreferences();
		if (!message.isImageUrl()) return false;
		if (!sharedPref.getBoolean("preview_image_urls_enabled", true)) return false;
		if (message.getConversation().getMode() == Conversation.MODE_SINGLE) {
			Contact theContact = message.getContact();
			Log.d(Config.LOGTAG, "shouldDisplayImagePreview, subscription of sender: " + Integer.toBinaryString(theContact.getSubscription()));
			if (!theContact.getOption(Contact.Options.IN_ROSTER)) return false;
		}
		return true;
	}


}
