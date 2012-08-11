package com.guitarbean.asi;

import java.net.URL;
import java.util.List;
import java.util.Map;

import com.guitarbean.asi.HTTPRequest.HTTPRequestHandler;

public abstract class HTTPRequestDelegate implements HTTPRequestHandler{

	public HTTPRequestDelegate() {
		super();
	}

	@Override
	public void requestStarted(HTTPRequest request) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public void requestDidReceiveResponseHeaders(HTTPRequest request,
			Map<String, List<String>> headers) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void requestFinished(HTTPRequest request) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void requestFailed(HTTPRequest request) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void requestDidReseiveData(HTTPRequest request, byte[] data) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void requestWillRedirectToURL(HTTPRequest request, URL newURL) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void requestRedirected(HTTPRequest request) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void authenticationNeededForRequest(HTTPRequest request) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void proxyAuthenticationNeededForRequest(HTTPRequest request) {
		// TODO Auto-generated method stub
		
	}
}
