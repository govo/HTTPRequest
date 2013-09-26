package com.guitarbean.asi;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;

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
        //TODO:StoragePage为NULL时，默认为APP缓存目录，如果输入名字，也默认在缓存目录之下的文件夹
	}

    public HTTPDownloadCache(Context context){
        super();
        this.storagePath = getCacheDirPathWithContext(context,"httpcache");
    }
    public HTTPDownloadCache(Context context,String name){
        super();
        this.storagePath = getCacheDirPathWithContext(context,name);
    }

    //返回缓存文件名
    public String fullPathNameOfTempFile(String filename){
        return fullPathNameOfTemp(HTTPRequest.parseToMd5String(filename));
    }
    //返回缓存文件名
    public String fullPathNameOfTemp(String md){
        return this.storagePath + File.separator + md+".temp";
    }
    //缓存文件信息文件夹
    public String fullPathMetaDirOfTemp(){
        return this.storagePath + File.separator + HTTPDownloadCache.META_DIR;
    }
    //缓存文件信息文件名
    public String fullPathMetaNameOfTemp(String md){
        return fullPathMetaDirOfTemp() + File.separator + md+".meta";

    }
	
	public static File getCacheDirWithContext(Context context, String subDir) {
		// Check if media is mounted or storage is built-in, if so, try and use external cache dir
		// otherwise use internal cache dir
		return new File(getCacheDirPathWithContext(context,subDir));
	}
	public static String getCacheDirPathWithContext(Context context, String subDir) {
		// Check if media is mounted or storage is built-in, if so, try and use
		// external cache dir
		// otherwise use internal cache dir
        final String state = Environment.getExternalStorageState();
        final String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            if (Build.VERSION.SDK_INT <= 7){
                cachePath = Environment.getExternalStorageDirectory().getPath() + "/Android/data/"+context.getPackageName()+"/cache/";
            }else {
                cachePath = context.getExternalCacheDir().getPath();
            }
        }else{
            cachePath = context.getCacheDir().getPath();
        }
        if(subDir!=null){
		    return cachePath + File.separator + subDir;
        }else{
            return cachePath;
        }
	}


}
