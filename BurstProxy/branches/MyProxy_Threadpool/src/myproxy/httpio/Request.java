/**
 * This file is part of MyProxy.
 * 
 * Copyright (C) 2002 Alexander Dietrich
 * 
 * MyProxy is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * MyProxy is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with MyProxy; if not, write to the Free Software Foundation,
 * Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package myproxy.httpio;

import java.io.*;
import java.util.*;
import java.util.regex.*;


/**
 * An HTTP request message (without body).
 */
public final class Request extends Message
{
	private final URIParser _uri;
	private String _method, _uriPath;

	public Request()
	{
		_uri = new URIParser();
	}
	
	public String getMethod()
	{
		return _method;
	}
	
	public void setMethod(String method)
	{
		_method = method;
	}
	
	public URIParser getURI()
	{
		return _uri;
	}
	
	/**
	 * Returns the path plus query and fragment, if any.
	 */
	public String getFullURIPath()
	{
		return _uriPath;
	}
	
	public void setURI(String uriString) throws MessageFormatException
	{
		parseURI(uriString);
	}
	
	public void clear()
	{
		super.clear();
		_method = null;
		
		try
		{
			setURI(null);
		}
		catch(MessageFormatException e)
		{
			// ignore
		}
	}
	
	public void read(InputStream in) throws IOException, MessageFormatException
	{
		clear();
		
		String requestLine = readLine(in);
		// skip empty lines, as per RFC 2616
		while(requestLine.length() == 0)
			requestLine = readLine(in);
			
		StringTokenizer t = new StringTokenizer(requestLine, " ");
		if(!(t.countTokens() == 3))
			throw new MessageFormatException("Invalid request line: " + requestLine);
		
		_method = t.nextToken();
		String uri = t.nextToken();
		if(_method.equalsIgnoreCase("connect") && !uri.toLowerCase().startsWith("https://"))
			uri="https://"+uri;
		parseURI(uri);
		
		String versionString = t.nextToken();
		Matcher httpVersion = HTTP_VERSION.matcher(versionString);
		if(!httpVersion.matches())
			throw new MessageFormatException("Unsupported protocol/version: " + versionString);
		setVersion(Integer.parseInt(httpVersion.group(1)), Integer.parseInt(httpVersion.group(2)));

		getHeaders().read(in);
	}
	
	public void write(OutputStream out) throws IOException
	{
		StringBuffer requestLine = new StringBuffer(80);
		requestLine.append(_method).append(' ');
		requestLine.append(_uri.getSource()).append(' ');
		requestLine.append("HTTP/").append(getMajorVersion()).append('.').append(getMinorVersion());
		
		writeLine(requestLine.toString(), out);
		getHeaders().write(out);
	}
	
	private void parseURI(String uriString) throws MessageFormatException
	{
		_uriPath = null;
		
		try
		{
			_uri.parse(uriString);
		}
		catch(URIFormatException e)
		{
			throw new MessageFormatException(e.getMessage());
		}
		
		if(_uri.getPath() != null)
		{
			StringBuffer relativeURI = new StringBuffer(_uri.getPath());
			if(_uri.getQuery() != null)
				relativeURI.append('?').append(_uri.getQuery());
			if(_uri.getFragment() != null)
				relativeURI.append('#').append(_uri.getFragment());
			_uriPath = relativeURI.toString();
		}		
	}
	
	public String toString()
	{
		StringBuffer value = new StringBuffer(_method.length() + _uri.getSource().length() + 12);
		value.append(_method).append(' ').append(_uri.getSource());
		value.append(" HTTP/").append(getMajorVersion()).append('.').append(getMinorVersion());
		return value.toString();
	}
	
	public static Request createFromURI(String uri) throws URIFormatException {
		return createFromURI(uri, "GET");
	}
	
	public static Request createFromURI(String uri, String method) throws URIFormatException {
		Request r = new Request();
		
		try {
			r.setURI(uri);
		} catch(MessageFormatException e) {
			throw new URIFormatException(e.getMessage(), uri);
		}
		
		r.setMethod(method);
		r.setVersion(1, 1);
		
		return r;
	}

}
