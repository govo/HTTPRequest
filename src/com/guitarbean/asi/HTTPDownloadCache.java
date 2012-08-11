package com.guitarbean.asi;

import java.io.File;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

public class HTTPDownloadCache {
	String storagePath;
	
	public final static String META_DIR = "_CacheMetaFile"; 
	
	
	// The default cache policy. When you set a request to use this, it will use the cache's defaultCachePolicy
	// ASIDownloadCache's default cache policy is 'ASIAskServerIfModifiedWhenStaleCachePolicy'
	public final static int UseDefaultCachePolicy = 0,

	// Tell the request not to read from the cache
	DoNotReadFromCacheCachePolicy = 1,

	// The the request not to write to the cache
	DoNotWriteToCacheCachePolicy = 2,

	// Ask the server if there is an updated version of this resource (using a conditional GET) ONLY when the cached data is stale
	AskServerIfModifiedWhenStaleCachePolicy = 4,

	// Always ask the server if there is an updated version of this resource (using a conditional GET)
	AskServerIfModifiedCachePolicy = 8,

	// If cached data exists, use it even if it is stale. This means requests will not talk to the server unless the resource they are requesting is not in the cache
	OnlyLoadIfNotCachedCachePolicy = 16,

	// If cached data exists, use it even if it is stale. If cached data does not exist, stop (will not set an error on the request)
	DontLoadCachePolicy = 32,

	// Specifies that cached data may be used if the request fails. If cached data is used, the request will succeed without error. Usually used in combination with other options above.
	FallbackToCacheIfLoadFailsCachePolicy = 64;
	

	int cachePolicy = UseDefaultCachePolicy;

	public String getStoragePath() {
		return storagePath;
	}

	public void setStoragePath(String storagePath) {
		this.storagePath = storagePath;
	}

	/**
	 * 
	 */
	public HTTPDownloadCache(String storagePath) {
		super();
		this.storagePath = storagePath;
	}
	
	public static File getCacheDirWithContext(Context context, String uniqueName) {
		// Check if media is mounted or storage is built-in, if so, try and use external cache dir
		// otherwise use internal cache dir
		final String cachePath = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
		        ? Environment.getExternalStorageDirectory().getPath() : context.getCacheDir().getPath();

		return new File(cachePath + File.separator + uniqueName);
	}
	public static String getCacheDirPathWithContext(Context context, String uniqueName) {
		// Check if media is mounted or storage is built-in, if so, try and use
		// external cache dir
		// otherwise use internal cache dir

		//Log.i("STORAGESTATE", Environment.getExternalStorageState()+","+Environment.MEDIA_MOUNTED+",");
		final String cachePath = Environment.getExternalStorageState().compareTo(Environment.MEDIA_MOUNTED)==0 ? Environment
				.getExternalStorageDirectory().getPath() : context
				.getCacheDir().getPath();
		return cachePath + File.separator + uniqueName;
	}


}
