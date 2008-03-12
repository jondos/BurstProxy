package myproxy.prefetching;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import myproxy.httpio.URIParser;

public class CSSParser implements PrefetchingParser {

	private static final Pattern URL_PATTERN = Pattern.compile(
			"url\\([\\s\"']*([^)]+?)[\\s\"']*\\)", Pattern.CASE_INSENSITIVE);
	
	
	public List findURLsInResponse(URIParser baseUrl, String responseBody) throws MalformedURLException {
		
		Matcher matcher = URL_PATTERN.matcher(responseBody);
		List matches = new ArrayList();
		URL context = new URL(baseUrl.getSource());
		
		while(matcher.find()) {
			URL url = new URL(context, matcher.group(1));
			if(url != null && !matches.contains(url.toExternalForm())) matches.add(url.toExternalForm());
		}
		
		return matches;

	}

}
