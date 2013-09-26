package com.guitarbean.asi;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import android.R.integer;
import android.util.Log;

import com.guitarbean.asi.HTTPRequest.HTTPRequestHandler;

public class HTTPRequestQueue{
	int capacity = 0;
	int size = 0;
	int currentIndex = -1;
	ArrayList<WeakReference<HTTPRequest>> arrayList;
	Boolean stopAllOnErrorBoolean = false;
	private RequestDelegateHandler delegateHandler;
	int tag =0;
	
	public int getTag() {
		return tag;
	}
	public void setTag(int tag) {
		this.tag = tag;
	}

	HTTPRequestQueueHandler queueHandler;
	
	public static int QUEUE_UNSTARTED = 0x00000001;
	public static int QUEUE_STARTED = 0x00000010;
	public static int QUEUE_FINISHED = 0x00000100;
	
	int queueStatus = QUEUE_UNSTARTED;

	
	/**
	 * 构造函数，创建一个队列
	 * @param capacity 最初容量
	 * @param queueHandler 代理对象
	 * @see HTTPRequestQueueHandler
	 */
	public HTTPRequestQueue(int capacity,HTTPRequestQueueHandler queueHandler) {
		this.capacity = capacity;
		arrayList = new ArrayList<WeakReference<HTTPRequest>>(capacity);
		this.queueHandler = queueHandler;
		delegateHandler = new RequestDelegateHandler();
	}
	/**
	 * 给队列添加HttpRequest，如果队列已经开始运行，则会自动添加到运行队列结尾等待运行
	 * @param request
	 */
	public void add(HTTPRequest request) {
		arrayList.add(new WeakReference<HTTPRequest>(request));
		request.setDelegate(delegateHandler);
		size++;
		if(this.queueStatus==QUEUE_FINISHED){
			this.queueStatus = QUEUE_STARTED;
			request.startAsynchronous();
		}
	}
	
	public void setStopWhenError(Boolean stop) {
		this.stopAllOnErrorBoolean = stop;
	}
	
	/**
	 * 运行队列
	 */
	public void startQueue(){
		if(this.queueStatus!=QUEUE_UNSTARTED || arrayList.size()==0) return;
		currentIndex++;
		arrayList.get(currentIndex).get().startAsynchronous();
		this.queueStatus = QUEUE_STARTED;
	}

	/**
	 * 停止所有未结束和未运行的队列
	 */
	public void stopAll() {
		if(arrayList.size()==0) return;
		for(int i = currentIndex;i<size;i++){
			HTTPRequest req = arrayList.get(currentIndex).get();
			if(req!=null) arrayList.get(currentIndex).get().cancel();
		}
		this.queueStatus = QUEUE_UNSTARTED;
	}
	
	private class RequestDelegateHandler implements HTTPRequestHandler{

		@Override
		public void requestStarted(HTTPRequest request) {
		}

		@Override
		public void requestDidReceiveResponseHeaders(HTTPRequest request,
				Map<String, List<String>> headers) {
		}

		@Override
		public void requestFinished(HTTPRequest request) {
			Log.i("HTTP_QUEUE","FINISHED:"+currentIndex+",size:"+size+",ArrayList.size:"+arrayList.size());
			int lastCount = currentIndex;
			HTTPRequestQueue.this.queueHandler.onFinished(request, lastCount);
			arrayList.get(lastCount).clear();

			currentIndex++;
			if (lastCount>=size-1) {
				HTTPRequestQueue.this.queueStatus = QUEUE_FINISHED;
				HTTPRequestQueue.this.queueHandler.onAllFinished();
			}else{
				arrayList.get(currentIndex).get().startAsynchronous();
			}
		}

		@Override
		public void requestFailed(HTTPRequest request) {
			Log.i("HTTP_QUEUE","FAILED:"+currentIndex);
			int lastCount = currentIndex;
			HTTPRequestQueue.this.queueHandler.onError(request, lastCount);
			if(HTTPRequestQueue.this.stopAllOnErrorBoolean) return;
			arrayList.get(lastCount).clear();

			currentIndex++;
			if (lastCount>=size-1) {
				HTTPRequestQueue.this.queueStatus = QUEUE_FINISHED;
				HTTPRequestQueue.this.queueHandler.onAllFinished();
			}else{
				arrayList.get(currentIndex).get().startAsynchronous();
			}
		}

		@Override
		public void requestDidReseiveData(HTTPRequest request, byte[] data) {
		}

		@Override
		public void requestWillRedirectToURL(HTTPRequest request, URL newURL) {
		}

		@Override
		public void requestRedirected(HTTPRequest request) {
		}

		@Override
		public void authenticationNeededForRequest(HTTPRequest request) {
		}

		@Override
		public void proxyAuthenticationNeededForRequest(HTTPRequest request) {
		}
		
	}
	
}
