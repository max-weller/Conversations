package eu.siacs.conversations.utils.http;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONTokener;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Proxy;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

public class HttpUploader extends Thread {
	
	private Runnable myCallback;
	//private Handler myHandler;
	private String myMethod;
	private List<NameValuePair> myPostData;
	private String myUrl;
	private String myResult;
	private int myHttpStatus;
	private Exception myError;
	private boolean success;
	private String twSession;
	private List<PostFile> myPostFiles;
	private boolean convertToBitmap = false;
	private String downloadToFile = null;
	private Bitmap myBmpResult;
	private String myProxyIp, myProxyUsername, myProxyPassword;
	private int myProxyPort;
	
	public static class PostFile {
		public static final int MAX_BUFFER_SIZE = 1*1024*1024;
		private String fieldName,remoteFilename,contentType;
		private File file;
		public PostFile(String postFieldName, String attachmentFileName, String contentType, String localFileName) {
			fieldName=postFieldName; remoteFilename=attachmentFileName; this.contentType=contentType;
			file=new File(localFileName);			
		}
		public PostFile(String postFieldName, String attachmentFileName, String contentType, File localFile) {
			fieldName=postFieldName; remoteFilename=attachmentFileName; this.contentType=contentType;
			file=localFile;			
		}
		public void writeToStream(DataOutputStream outputStream, String boundary) throws IOException {
			outputStream.writeBytes("--" + boundary + "\r\n");
			outputStream.writeBytes("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + remoteFilename +"\"" + "\r\n");
			outputStream.writeBytes("Content-Type: "+contentType+"\r\n");

			outputStream.writeBytes("\r\n");

			FileInputStream fileInputStream = new FileInputStream(file);
			int bytesAvailable = fileInputStream.available();
			int bufferSize = Math.min(bytesAvailable, MAX_BUFFER_SIZE);
			byte[] buffer = new byte[bufferSize];

			int bytesRead = fileInputStream.read(buffer, 0, bufferSize); // Read file

			while (bytesRead > 0) {
				outputStream.write(buffer, 0, bufferSize);
				bytesAvailable = fileInputStream.available();
				bufferSize = Math.min(bytesAvailable, MAX_BUFFER_SIZE);
				bytesRead = fileInputStream.read(buffer, 0, bufferSize);
			}

			outputStream.writeBytes("\r\n");
			fileInputStream.close();
		}
	}
	
	public void addPostFile(PostFile file) {
		if (myPostFiles == null) myPostFiles = new ArrayList<PostFile>();
		myPostFiles.add(file);
	}

	public void updateProxySettings(Context ctx) {
		myProxyIp = Proxy.getHost(ctx);
		myProxyPort = Proxy.getPort(ctx);

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		myProxyUsername = prefs.getString("proxy_username", null);
		myProxyPassword = prefs.getString("proxy_password", null);
		Log.i("TwAjax", "PROXY SETTINGS:");
		Log.i("TwAjax", "Host=" + myProxyIp);
		Log.i("TwAjax", "Port=" + myProxyPort);
		Log.i("TwAjax", "User=" + myProxyUsername);
		
	}
	
	public HttpUploader() {
	}
	public HttpUploader(String sessionID) {
		twSession = sessionID;
	}
	public HttpUploader(Context ctx, boolean updateProxySettings, boolean initializeLoginData) {
		if (initializeLoginData) this.initializeLoginData(ctx);
		if (updateProxySettings) this.updateProxySettings(ctx);
	}
	
	public String getURL() {
		return myUrl;
	}
	public void setURL(String newUrl) {
		myUrl = newUrl;
	}
	public String getMethod() {
		return myMethod;
	}
	public void setMethod(String newMethod) {
		myMethod = newMethod;
	}
	public List<NameValuePair> getPostData() {
		return myPostData;
	}
	public void setPostData(List<NameValuePair> newPostData) {
		myPostData = newPostData;
	}
	public void addPostData(String key, String value) {
		if (myPostData == null) myPostData = new ArrayList<NameValuePair>();
		myPostData.add(new BasicNameValuePair(key, value));
	}
	public boolean initializeLoginData(Context ctx) {
		this.addPostData("key", "dbb96352");//always set Dropme.de api key

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
		String userName = prefs.getString("login_user", null);
		if (userName == null || userName.length() < 1) return false;
		this.addPostData("username", userName);
		this.addPostData("password", prefs.getString("login_password", null));
		return true;
	}
	public int getHttpCode() {
		return myHttpStatus;
	}
	public boolean isSuccess() {
		return success;
	}
	public Exception getError() {
		return myError;
	}
	public Object getJsonResult() {
		if (!success) return null;
		try {
			JSONTokener jt = new JSONTokener(myResult);
			return jt.nextValue();
		} catch (JSONException ex) {
			//server returned malformed data
			return null;
		}
	}
	public String getResult() {
		return myResult;
	}
	public Bitmap getBitmapResult() {
		return myBmpResult;
	}
	
	public void run() {
        try {
			if (myPostFiles == null) {
				runDefault();
			} else {
				runFileUpload();
			}
        } catch (Exception e) {
        	success=false;
        	myError = e;
        }
        //if (myHandler != null && myCallback != null) myHandler.post(myCallback);
		myCallback.run();
	}
	
    private void setHttpClientProxy(DefaultHttpClient httpclient) {  
		
		
    	if (myProxyIp == null) return;
    	
        httpclient.getCredentialsProvider().setCredentials(  
                new AuthScope(myProxyIp, myProxyPort),  
                new UsernamePasswordCredentials(  
                        myProxyUsername, myProxyPassword));  

       HttpHost proxy = new HttpHost(myProxyIp, myProxyPort);  

       httpclient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);  


    }  
	
	private void runDefault() throws IOException {
		// Create a new HttpClient and Get/Post Header
		DefaultHttpClient httpclient = new DefaultHttpClient();
        setHttpClientProxy(httpclient);
        httpclient.getParams().setParameter("http.protocol.content-charset", "UTF-8");
        HttpRequestBase m;
        if (myMethod == "POST") {
	        m = new HttpPost(myUrl);
	        ((HttpPost)m).setEntity(new UrlEncodedFormEntity(myPostData));
        } else {
        	m = new HttpGet(myUrl);
        }
        if (twSession != null) m.addHeader("Cookie", "twnetSID=" + twSession);
        // Execute HTTP Get/Post Request
        HttpResponse response = httpclient.execute(m);
        //InputStream is = response.getEntity().getContent();
        myHttpStatus = response.getStatusLine().getStatusCode();
        if (this.downloadToFile != null) {
            // download the file
            InputStream input = new BufferedInputStream(response.getEntity().getContent());
            OutputStream output = new FileOutputStream(downloadToFile);

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
        } else if (this.convertToBitmap) {
        	myBmpResult = BitmapFactory.decodeStream(response.getEntity().getContent());
        } else {
            myResult = EntityUtils.toString(response.getEntity(), "UTF-8");
        }
        //BufferedInputStream bis = new BufferedInputStream(is);
        //ByteArrayBuffer baf = new ByteArrayBuffer(50);

        //int current = 0;
        //while((current = bis.read()) != -1){
        //    baf.append((byte)current);
        //}

        //myResult = new String(baf.toByteArray(), "utf-8");
        success=true;
	}
	private void runFileUpload() throws IOException {

		String lineEnd = "\r\n";
		String twoHyphens = "--";
		String boundary =  "d1934afa-f2e4-449b-99be-8be6ebfec594";

		URL url = new URL(getURL());
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		// Allow Inputs & Outputs
		connection.setDoInput(true);
		connection.setDoOutput(true);
		connection.setUseCaches(false);

		// Enable POST method
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Cookie", "twnetSID="+twSession);
		connection.setRequestProperty("Connection", "Keep-Alive");
		connection.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);

		DataOutputStream outputStream = new DataOutputStream( connection.getOutputStream() );
		if (myPostData != null) {
			for (NameValuePair nvp : myPostData) {
				outputStream.writeBytes(twoHyphens + boundary + lineEnd);
				outputStream.writeBytes("Content-Disposition: form-data; name=\"" + nvp.getName() + "\"" + lineEnd);
				outputStream.writeBytes(lineEnd + nvp.getValue() + lineEnd);
			}
		}
		for(PostFile pf : myPostFiles) {
			pf.writeToStream(outputStream, boundary);
		}
		outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

		// Responses from the server (code and message)
		myHttpStatus = connection.getResponseCode();

		outputStream.flush();
		outputStream.close();
		
		if (myHttpStatus < 400) {
			myResult = convertStreamToString(connection.getInputStream());
		} else {
			myResult = convertStreamToString(connection.getErrorStream());
		}
		
        success=true;
	}
    public String convertStreamToString(InputStream is) throws IOException {
		/*
		 * To convert the InputStream to String we use the
		 * Reader.read(char[] buffer) method. We iterate until the
		 * Reader return -1 which means there's no more data to
		 * read. We use the StringWriter class to produce the string.
		 */
		if (is != null) {
			StringWriter writer = new StringWriter();

		    char[] buffer = new char[1024];
		    try {
		    	BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
		        int n;
		        while ((n = reader.read(buffer)) != -1) {
		            writer.write(buffer, 0, n);
		        }
		    } finally {
		        is.close();
		    }
		    return writer.toString();
		} else {        
		    return "";
		}
	}
	public void getUrlContent(String url, Runnable callback) {
		this.myMethod = "GET";
		this.myUrl = url;
		this.myCallback = callback;
		if (callback != null) {
			//this.myHandler = new Handler();
			this.start();
		} else {
			this.run();
		}
	}
	public void getUrlBitmap(String url, Runnable callback) {
		this.myMethod = "GET";
		this.myUrl = url;
		this.myCallback = callback;
		this.convertToBitmap = true;
		if (callback != null) {
			//this.myHandler = new Handler();
			this.start();
		} else {
			this.run();
		}
	}
	public void urlDownloadToFile(String url, String targetFileSpec, Runnable callback) {
		this.myMethod = "GET";
		this.myUrl = url;
		this.myCallback = callback;
		this.downloadToFile = targetFileSpec;
		if (callback != null) {
			//this.myHandler = new Handler();
			this.start();
		} else {
			this.run();
		}
	}
	public void urlDownloadToFile(String url, String targetFileSpec, Runnable callback, String method) {
		this.myMethod = method;
		this.myUrl = url;
		this.myCallback = callback;
		this.downloadToFile = targetFileSpec;
		if (callback != null) {
			//this.myHandler = new Handler();
			this.start();
		} else {
			this.run();
		}
	}
	public void uploadFile(String url, Runnable callback) {
		try {
			this.myMethod = "POST";
			this.myUrl = url;
			this.myCallback = callback;
			if (callback != null) { //async
				//this.myHandler = new Handler();
				this.start();
			} else { //sync
				this.run();
			}
		} catch (Exception e) {
			success=false; myError=e; if (callback != null) callback.run();
		}
	}
	public void postData(String url, Runnable callback) {
		try {
			this.myMethod = "POST";
			this.myUrl = url;
			this.myCallback = callback;
			if (callback != null) { //async
				//this.myHandler = new Handler();
				this.start();
			} else { //sync
				this.run();
			}
		} catch (Exception e) {
			success=false; myError=e; if (callback != null) callback.run();
		}
	}
	
}
