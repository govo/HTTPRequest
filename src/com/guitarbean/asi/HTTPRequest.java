/**
 * 
 */
package com.guitarbean.asi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import org.apache.http.util.ByteArrayBuffer;

import android.R.bool;
import android.R.integer;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * @author govo
 *
 */
public class HTTPRequest implements Runnable {

	URL url;
	BlockingQueue<HTTPRequest> queue;
	Thread thread;
	Handler handler;
	HttpURLConnection connection;
	HTTPRequestHandler delegate;
	byte[] result;
	boolean forceStop = false;
	int total = 0;
	int percent=0;
	int threadStatus=-1;
	Exception exception;
	Map<String, List<String>> headers;
	HTTPDownloadCache cache;
	int policy;
	String fileName;
	String fullPathDir;
	String fullPathName;
	String fullPathMetaDir;
	String fullPathMetaName;
	HeaderMeta headerMetaFromCache;
	boolean thisSessionHeaderMetaWrited=false;
	

	public HTTPDownloadCache getCache() {
		return cache;
	}

	public void setCache(HTTPDownloadCache cache) {
		this.cache = cache;
	}


	public URL getUrl() {
		return url;
	}


	public void setUrl(URL url) {
		this.url = url;
	}
	
	public HTTPRequestHandler getDelegate() {
		return delegate;
	}


	public void setDelegate(HTTPRequestHandler delegate) {
		this.delegate = delegate;
	}
	public final static int DID_START=1;
	public final static int DID_RECEIVE_HEADER=2;
	public final static int DID_RECEIVE_DATA=3;
	public final static int DID_FINISHED=4;
	public final static int DID_FAILED=0;
	
	final static String bytesKey = "bytes";
	
	public HTTPRequest(URL url) {
		super();
		this.url = url;
	}
	public static HTTPRequest newRequest(URL url,HTTPDownloadCache cache) {
		return newRequest(url, cache, HTTPDownloadCache.UseDefaultCachePolicy);
	}
	public static HTTPRequest newRequest(URL url,HTTPDownloadCache cache,int policy) {
		HTTPRequest request = new HTTPRequest(url);
		request.url = url;
		request.cache = cache;
		request.policy = policy;
		return request;
	}
	

	/* run() 是所有操作的唯一入口
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		forceStop = false;
		exception = null;
		headers = null;
		thisSessionHeaderMetaWrited=false;
		connection = null;
		result = null;
		Message message;
		message = new Message();
		Bundle bundle = new Bundle();
		message.setData(bundle);
		if (handler==null) {
			handler = new Handler();
		}
		message.what = threadStatus = DID_START;
		handler.sendMessage(message);

		fileName = parseToMD5Code(url.toString());
		fullPathName = null;
		fullPathMetaName = null;
		if (fileName!=null && cache!=null && cache.storagePath!=null) {
			fullPathName = cache.storagePath + File.separator + fileName+".temp";
			fullPathMetaDir = cache.storagePath + File.separator + HTTPDownloadCache.META_DIR;
			fullPathMetaName =fullPathMetaDir + File.separator + fileName+".meta";
		}
		
		
		switch (policy) {
		case HTTPDownloadCache.AskServerIfModifiedCachePolicy:
		case HTTPDownloadCache.AskServerIfModifiedCachePolicy | HTTPDownloadCache.FallbackToCacheIfLoadFailsCachePolicy:
			// 向检查是否未过期
			if (fullPathName != null && new File(fullPathName).exists()
					&& !needUpdate()
					&& (result = getFileFromeDiskCache()) != null) {
				if (forceStop) {
					disConnect();
					return;
				}
				sendSuccessedMessage();
				return;
			}
			break;
		case HTTPDownloadCache.DoNotReadFromCacheCachePolicy:
		case HTTPDownloadCache.DoNotReadFromCacheCachePolicy | HTTPDownloadCache.FallbackToCacheIfLoadFailsCachePolicy:
			break;
		case HTTPDownloadCache.OnlyLoadIfNotCachedCachePolicy:
		case HTTPDownloadCache.OnlyLoadIfNotCachedCachePolicy | HTTPDownloadCache.FallbackToCacheIfLoadFailsCachePolicy:
			// 只要有cache文件，就加载之
			if (fullPathName != null && new File(fullPathName).exists()
					&& (result = getFileFromeDiskCache()) != null) {
				if (forceStop) {
					disConnect();
					return;
				}
				sendSuccessedMessage();
				return;
			}
			break;
		case HTTPDownloadCache.DontLoadCachePolicy:
		case HTTPDownloadCache.DontLoadCachePolicy | HTTPDownloadCache.FallbackToCacheIfLoadFailsCachePolicy:
			//只读cache，如果无，不返回任何信息
			if (fullPathName != null && new File(fullPathName).exists()
					&& (result = getFileFromeDiskCache()) != null) {
				if (forceStop) {
					disConnect();
					return;
				}
				sendSuccessedMessage();
			}
			return;
			
		case HTTPDownloadCache.UseDefaultCachePolicy:
		case HTTPDownloadCache.AskServerIfModifiedWhenStaleCachePolicy:
		case HTTPDownloadCache.FallbackToCacheIfLoadFailsCachePolicy:
		case HTTPDownloadCache.FallbackToCacheIfLoadFailsCachePolicy | HTTPDownloadCache.AskServerIfModifiedWhenStaleCachePolicy:
		default:
			// 未过期而且cache文件存在
			if (fullPathName != null && new File(fullPathName).exists()
					&& !isExpiredAndNeedUpdate()
					&& (result = getFileFromeDiskCache()) != null) {
				if (forceStop) {
					disConnect();
					return;
				}
				sendSuccessedMessage();
				return;
			}
			break;
		}
		if (forceStop) {
			disConnect();
			return;
		}
		//建立链接
		if (!initConnection()) {
			return;
		}
		
			
		byte[] buffer=new byte[1024];
		int length = 0, loaded=0;
		total = connection.getContentLength();
		prepareHeaderFromConnection();

		try {
			InputStream in = connection.getInputStream();
			ByteArrayOutputStream os = new ByteArrayOutputStream(total);
			while ((length = in.read(buffer))!=-1 && !forceStop) {
				os.write(buffer,0,length);
				loaded+=length;
				message = handler.obtainMessage();
				bundle.putByteArray(bytesKey, buffer);
				message.setData(bundle);
				message.what = threadStatus = DID_RECEIVE_DATA;
				handler.sendMessage(message);
				percent = (int) loaded*100/total;
			}
			if (forceStop) {
				disConnect();
				os.close();
				in.close();
				return;
			}
			bundle.putByteArray(bytesKey, result = os.toByteArray());
			
			sendSuccessedMessage();
			
			
			os.close();
			in.close();
			
		} catch (Exception e) {
			e.printStackTrace();
			if (forceStop) {
				disConnect();
				return;
			}
			switch (policy) {
			//只要有FallbackToCacheIfLoadFails就返回缓存
			case HTTPDownloadCache.FallbackToCacheIfLoadFailsCachePolicy:
			case HTTPDownloadCache.FallbackToCacheIfLoadFailsCachePolicy|HTTPDownloadCache.AskServerIfModifiedWhenStaleCachePolicy:
			case HTTPDownloadCache.FallbackToCacheIfLoadFailsCachePolicy|HTTPDownloadCache.AskServerIfModifiedCachePolicy:
			case HTTPDownloadCache.FallbackToCacheIfLoadFailsCachePolicy|HTTPDownloadCache.DoNotWriteToCacheCachePolicy:
				if (fullPathName != null && new File(fullPathName).exists()
						&& (result = getFileFromeDiskCache()) != null) {
					if (forceStop) {
						disConnect();
						return;
					}
					sendSuccessedMessage();
				}else{
					Log.i("FallbackToCacheIfLoadFailsCachePolicy", ""+e);
					sendFailedMessage(e);
					return;
				}
				break;

			default:
				break;
			}
			Log.i("runn connect", ""+e);
			sendFailedMessage(e);
		}finally{
			writeToCache();
		}
	}
	private boolean writeToCache() {
		boolean isWrited=false;
		if (policy!=HTTPDownloadCache.DoNotWriteToCacheCachePolicy && fullPathName!=null && result!=null) {
			File dir = new File(cache.storagePath);
			if (!dir.exists()) {
				dir.mkdir();
			}
			writeHeaderMeta();
			File newFile = new File(fullPathName);

			if (newFile.exists()) {
				newFile.delete();
			}
			FileOutputStream fos;
			try {
				fos = new FileOutputStream(fullPathName);
				fos.write(result);
				fos.close();
				isWrited = true;
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return isWrited;
	}
	
	private byte[] getFileFromeDiskCache() {
		if (fullPathName == null) {
			return null;
		}
		try {
			FileInputStream fis = new FileInputStream(fullPathName);
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int len = 0;
			while ((len = fis.read(buffer)) != -1) {
				bos.write(buffer, 0, len);
			}
			return bos.toByteArray();
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	void writeHeaderMeta(){
		if (thisSessionHeaderMetaWrited
				|| fullPathMetaName == null
				|| policy == HTTPDownloadCache.DoNotReadFromCacheCachePolicy
				|| policy == HTTPDownloadCache.DoNotWriteToCacheCachePolicy
				|| policy == HTTPDownloadCache.OnlyLoadIfNotCachedCachePolicy
				|| policy == HTTPDownloadCache.DontLoadCachePolicy
				|| !initConnection()) {
			return;
		}
		File dir = new File(fullPathMetaDir);
		if (!dir.exists()) {
			dir.mkdir();
		}
		Log.i("headMeta:",""+ fullPathMetaName);
		HeaderMeta meta = makeHeaderMeta();
		Log.i("meta",""+meta);
		try {
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(fullPathMetaName));
			oos.writeObject(meta);
			oos.close();
			thisSessionHeaderMetaWrited = true;
		} catch (FileNotFoundException e) {
			thisSessionHeaderMetaWrited = false;
			e.printStackTrace();
		} catch (IOException e) {
			thisSessionHeaderMetaWrited = false;
			e.printStackTrace();
		}finally{
			
		}
	}
	HeaderMeta getHeaderMeta(){
		HeaderMeta meta=null;
		try {
			ObjectInputStream in = new ObjectInputStream(new FileInputStream(fullPathMetaName));
			meta = (HeaderMeta) in.readObject();
			in.close();
		} catch (StreamCorruptedException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}finally{
			meta=null;
		}
		return meta;
	}
	//发送成功消息
	private void sendSuccessedMessage() {
		Message message = handler.obtainMessage();
		Bundle bundle = message.getData();
		bundle.putByteArray(bytesKey, result);
		message.what = threadStatus =DID_FINISHED;
		handler.sendMessage(message);
	}
	private void sendFailedMessage(Exception exception) {
		this.exception = exception;
		Message message = handler.obtainMessage();
		message.what = threadStatus = DID_FAILED;
		handler.sendMessage(message);
	}
	
	private boolean initConnection() {
		if (connection==null) {
			try {
				connection =(HttpURLConnection) url.openConnection();
				return true;
			} catch (IOException e) {
				Log.i("initConnection", ""+e.getLocalizedMessage());
				sendFailedMessage(e);
				e.printStackTrace();
				return false;
			}
		}
		return true;
	}
	//准备header
	private void prepareHeaderFromConnection() {
		if (headers==null) {
			Message message = handler.obtainMessage();
			headers = connection.getHeaderFields();
			message = handler.obtainMessage();
			message.what=DID_RECEIVE_HEADER;
			handler.sendMessage(message);
		}
	}
	//本地文件是否过期
	private boolean isExpired() {
		if (headerMetaFromCache==null) {
			return true;
		}
		return new Date().getTime()>headerMetaFromCache.getExpiration();
	}
	//如果本地文件过期，则查看服务器文件是否未过期
	private boolean isExpiredAndNeedUpdate(){
		if (headerMetaFromCache==null) {
			writeHeaderMeta();
			return true;
		}
		if (new Date().getTime()>headerMetaFromCache.getExpiration()) {
			prepareHeaderFromConnection();
			Map<String, List<String>> headers = HTTPRequest.this.headers;
			//check from ETag first
			String ETag = getETagFromHeader(headers);
			String mETag = headerMetaFromCache.getETag();
			if (ETag!=null && mETag!=null) {
				return ETag.compareTo(mETag)!=0;
			}
			HttpURLConnection connection = HTTPRequest.this.connection;
			//check lastModified
			long lastModified = connection.getLastModified();
			long mLastModified = headerMetaFromCache.getLastModified();
			if (lastModified!=0 && mLastModified!=0) {
				return lastModified>mLastModified;
			}
			writeHeaderMeta();
			return true;
		}else {
			return false;
		}
	}
	//直接检查服务器文件是否过期
	private boolean needUpdate() {
		if (headerMetaFromCache==null) {
			writeHeaderMeta();
			return true;
		}
		prepareHeaderFromConnection();
		Map<String, List<String>> headers = HTTPRequest.this.headers;
		//check from ETag first
		String ETag = getETagFromHeader(headers);
		String mETag = headerMetaFromCache.getETag();
		if (ETag!=null && mETag!=null) {
			return ETag.compareTo(mETag)!=0;
		}
		HttpURLConnection connection = HTTPRequest.this.connection;
		//check lastModified
		long lastModified = connection.getLastModified();
		long mLastModified = headerMetaFromCache.getLastModified();
		if (lastModified!=0 && mLastModified!=0) {
			return lastModified>mLastModified;
		}
		return true;
	}
	
	public void setDownloadCachePolicy(int policy) {
		this.policy = policy;
	}
	
	public void	startSynchronous() {
		this.run();
	}
	public void cancel() {
		disConnect();
		forceStop = true;
		if (thread!=null) {
			thread.stop();
			thread.destroy();
		}
	}
	private void disConnect() {
		if (connection!=null) {
			connection.disconnect();
			connection=null;
		}
	}

	public void startAsynchronous() {
		handler = new InnerHandler();
		thread = new Thread(this);
		thread.start();
	}
	public byte[] responseAsByteArray() {
		return result;
	}
	public String responseAsString() {
		if (result==null) {
			return null;
		}
		return new String(result);
	}
	public String responseAsString(String encoding) {
		if (result==null) {
			return null;
		}
		String rString=null;
		try {
			rString = new String(result,encoding);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return rString;
	}
	public int responsePercent() {
		return percent;
	}
	public Bitmap responseAsBitmap() {
		if (result==null) {
			return null;
		}
		return BitmapFactory.decodeByteArray(result, 0, result.length);
	}
	public Exception error() {
		return exception;
	}
	public int responseStatusCode() {
		try {
			return connection.getResponseCode();
		} catch (IOException e) {
			e.printStackTrace();
			return -1;
		}
	}
	public String responseMessage() {
		try {
			return connection.getResponseMessage();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	private class InnerHandler extends Handler{

		@Override
		public void dispatchMessage(Message msg) {
			// TODO Auto-generated method stub
			super.dispatchMessage(msg);
		}

		@Override
		public void handleMessage(Message msg) {
			if (delegate==null) {
				return;
			}
			super.handleMessage(msg);
			//Log.i("handleMessage",""+msg.what);
			Bundle bundle = msg.getData();
			switch (msg.what) {
			case DID_START:
				delegate.requestStarted(HTTPRequest.this);
				break;
			case DID_FINISHED:
				delegate.requestFinished(HTTPRequest.this);
				break;
			case DID_RECEIVE_DATA:
				if (bundle!=null) {
					delegate.requestDidReseiveData(HTTPRequest.this, bundle.getByteArray(bytesKey));
				}
				break;
			case DID_RECEIVE_HEADER:
				delegate.requestDidReceiveResponseHeaders(HTTPRequest.this, headers);
				break;
			case DID_FAILED:
				delegate.requestFailed(HTTPRequest.this);
				break;
			}
		}

		@Override
		public boolean sendMessageAtTime(Message msg, long uptimeMillis) {
			return super.sendMessageAtTime(msg, uptimeMillis);
		}
		
	}
	public interface HTTPRequestHandler{
		public void	requestStarted(HTTPRequest request);
		public void requestDidReceiveResponseHeaders(HTTPRequest request,Map<String, List<String>> headers);
		public void requestFinished(HTTPRequest request);
		public void requestFailed(HTTPRequest request);
		public void requestDidReseiveData(HTTPRequest request,byte[] data);
		public void requestWillRedirectToURL(HTTPRequest request,URL newURL);
		public void requestRedirected(HTTPRequest request);
		
		public void authenticationNeededForRequest(HTTPRequest request);
		public void proxyAuthenticationNeededForRequest(HTTPRequest request);
	}

	static String parseToMD5Code(String s){
		MessageDigest digest;
		try {
			digest = java.security.MessageDigest.getInstance("MD5");
			digest.reset();
			digest.update(s.getBytes());
			byte messageDigest[] = digest.digest(); 
			// Create Hex String    
	        StringBuffer hexString = new StringBuffer();
	        int digLength=messageDigest.length;
	        for (int i=0; i<digLength; i++)    
	            hexString.append(Integer.toHexString(0xFF & messageDigest[i]));
	        return hexString.toString();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	static String getETagFromHeader(Map<String, List<String>> header){
		List<String> list=null;
		if (header!=null && (list = header.get("ETag"))!=null && list.size()>0) {
			return list.get(0);
		}
		return null;
	}
	HeaderMeta makeHeaderMeta() {
		HttpURLConnection connection = HTTPRequest.this.connection;
		if (connection != null) {
			return new HeaderMeta(connection.getExpiration(),
					connection.getLastModified(), connection.getContentType(),
					getETagFromHeader(HTTPRequest.this.headers));
		} else {
			return null;
		}
	}
	public static class HeaderMeta implements Serializable{
		long expiration;
		long lastModified;
		String contentType;
		String ETag = null;
		
		public String getETag() {
			return ETag;
		}

		public void setETag(String eTag) {
			ETag = eTag;
		}

		public long getExpiration() {
			return expiration;
		}

		public void setExpiration(long expiration) {
			this.expiration = expiration;
		}

		public long getLastModified() {
			return lastModified;
		}

		public void setLastModified(long lastModified) {
			this.lastModified = lastModified;
		}

		public String getContentType() {
			return contentType;
		}

		public void setContentType(String contentType) {
			this.contentType = contentType;
		}

		
		
		/**
		 * 
		 */
/*		public HeaderMeta() {
			super();
			HttpURLConnection connection = HTTPRequest.this.connection;
			if (connection!=null) {
				expiration 		= connection.getExpiration();
				lastModified	= connection.getLastModified();
				contentType		= connection.getContentType();
				ETag = getETagFromHeader(HTTPRequest.this.headers);
			}
		}*/
		
		public HeaderMeta(long expiration,long lastModified,String contentType,String ETag) {
			this.expiration = expiration;
			this.lastModified = lastModified;
			this.contentType = contentType;
			this.ETag = ETag;
		}
		

		@Override
		public String toString() {
			return "["+this.getClass().getName()+
					": expiration="+expiration+
					" lastModified="+lastModified+
					" contentType="+contentType+
					" ETag="+ETag
					+"]";
		}
		
	}
}
