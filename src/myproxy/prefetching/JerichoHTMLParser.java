package myproxy.prefetching;

import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import au.id.jericho.lib.html.Element;
import au.id.jericho.lib.html.HTMLElementName;
import au.id.jericho.lib.html.Source;
import au.id.jericho.lib.html.StartTag;

import myproxy.httpio.URIParser;

public class JerichoHTMLParser implements PrefetchingParser {
	
	public List findURLsInResponse(URIParser baseUrl, String responseBody) throws MalformedURLException {
		final List matches = new ArrayList();
		final URL context = new URL(baseUrl.getSource());
		List tags;
		URL url;
		StartTag tag;
		
		try {
			Source htmlSource = new Source(new StringReader(responseBody));
			htmlSource.ignoreWhenParsing(new ArrayList());
			Iterator i;

			
			tags = htmlSource.findAllStartTags();
			
			for (i=tags.iterator(); i.hasNext();) {
				tag=(StartTag)i.next();
				
				if(tag.getName() == HTMLElementName.LINK) {
				
					String rel = tag.getAttributeValue("rel");
					String type = tag.getAttributeValue("type");
					String href = tag.getAttributeValue("href");
					if(href != null && (rel != null && rel.equals("stylesheet")) ||
							(type != null && type.equals("text/css")) ) {
						try {
							url =  new URL(context, href);
							if(url != null && !matches.contains(url.toExternalForm())) matches.add(url.toExternalForm());
						} catch (MalformedURLException e) {	}
					}
				}
				else if(tag.getName() == HTMLElementName.IMG) {
					try {
						url =  new URL(context, tag.getAttributeValue("src"));
						if(url != null && !matches.contains(url.toExternalForm())) matches.add(url.toExternalForm());
					} catch (MalformedURLException e) {	}
				}
				else if(tag.getName() == HTMLElementName.SCRIPT) {
					try {
						String src = tag.getAttributeValue("src");
						if(src!=null) {
							url =  new URL(context, src);
							if(url != null && !matches.contains(url.toExternalForm())) matches.add(url.toExternalForm());
						}
					} catch (MalformedURLException e) {	}
				}
				
				// look for URLs in <style>...</style>
				// note that Jericho HTML Parser does not parse contents of <style> tags
				// therefore, we have to extract the contents ourselves for parsing
				else if(tag.getName() == HTMLElementName.STYLE) {
					Element styleElement = tag.getElement();
					int start=styleElement.getStartTag().getBegin();
					int end=styleElement.getEndTag().getEnd();
					String cssContents = responseBody.substring(start, end); 
					
					CSSParser cssParser = new CSSParser();
					List urlsInCSS = cssParser.findURLsInResponse(baseUrl, cssContents);
					for(Iterator it=urlsInCSS.iterator();it.hasNext();) {
						String cssURL = (String)it.next();
						if(cssURL != null && !matches.contains(cssURL)) matches.add(cssURL);
					}
				}
				
				// look for urls contained in the STYLE="..." attributes of all tags
				// NOTE: this is VERY time-consuming and has been disabled for the time being
				/*String styleAttribute = tag.getAttributeValue("style");
				if(styleAttribute!=null) {
					CSSParser cssParser = new CSSParser(baseUrl, styleAttribute);
					List urlsInCSS = cssParser.findURLsInResponse();
					for(Iterator it=urlsInCSS.iterator();it.hasNext();) {
						String cssURL = (String)it.next();
						if(cssURL != null && !matches.contains(cssURL)) matches.add(cssURL);
					}
				}*/
			}

		} catch (IOException e) {
			throw new MalformedURLException(e.getMessage());
		}
		return matches;
	}

}
