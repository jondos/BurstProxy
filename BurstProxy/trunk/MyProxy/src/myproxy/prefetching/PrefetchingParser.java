package myproxy.prefetching;

import java.net.MalformedURLException;
import java.util.List;

import myproxy.httpio.URIParser;

public interface PrefetchingParser {
	public List findURLsInResponse(URIParser baseUrl, String responseBody) throws MalformedURLException;
}