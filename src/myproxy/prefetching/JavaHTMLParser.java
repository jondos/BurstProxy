package myproxy.prefetching;

import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

import myproxy.httpio.URIParser;

public class JavaHTMLParser implements PrefetchingParser {

	
	public List findURLsInResponse(URIParser baseUrl, String responseBody) throws MalformedURLException {
		final List matches = new ArrayList();
		final URL context = new URL(baseUrl.getSource());

		HTMLEditorKit.ParserCallback callback = 
			new HTMLEditorKit.ParserCallback () {
			
			private void parse(HTML.Tag tag, MutableAttributeSet attrSet, int pos) {
				URL url = null; 
				if (tag == HTML.Tag.IMG) {
					String src = (String)attrSet.getAttribute(HTML.Attribute.SRC);
					if(src != null)
						try {
							url =  new URL(context, src);
						} catch (MalformedURLException e) {
							url = null;
						}
				}
				else if (tag == HTML.Tag.LINK) {
					String rel = (String)attrSet.getAttribute(HTML.Attribute.REL);
					String type = (String)attrSet.getAttribute(HTML.Attribute.TYPE);
					String href = (String)attrSet.getAttribute(HTML.Attribute.HREF);
					if(href != null && (rel != null && rel.equals("stylesheet")) ||
							(type != null && type.equals("text/css")) ) {
						try {
							url =  new URL(context, href);
						} catch (MalformedURLException e) {
							url = null;
						}
					}
				}
				else if (tag == HTML.Tag.SCRIPT) {
					String src = (String)attrSet.getAttribute(HTML.Attribute.SRC);
					if(src!=null)
						try {
							url =  new URL(context, src);
						} catch (MalformedURLException e) {
							url = null;
						}
				}
				if(url != null && !matches.contains(url.toExternalForm())) matches.add(url.toExternalForm());
			}
			
			public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrSet, int pos) {
				parse(tag, attrSet, pos);
			}

			
			public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attrSet, int pos) {
				parse(tag, attrSet, pos);
			}
		};
		
		try {
			new ParserDelegator().parse(new StringReader(responseBody), callback, true);
		} catch (IOException e) {
			System.err.println(e.toString());
		}
		return matches;
	}
}
