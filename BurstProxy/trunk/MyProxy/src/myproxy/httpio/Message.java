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
import java.util.regex.*;

/**
 * The base class for <code>Request</code> and <code>Response</code>.
 */
public abstract class Message
{
	static final byte[] CRLF = { '\r', '\n' };
	static final Pattern HTTP_VERSION = Pattern.compile("HTTP/(\\d+)\\.(\\d+)");
	
	private final Headers _headers, _trailer;
	private int _majorVersion, _minorVersion;

	public Message()
	{
		_headers = new Headers();
		_trailer = new Headers();
	}
	
	/**
	 * Reads the message start line and headers from <tt>in</tt>.
	 */
	public abstract void read(InputStream in) throws IOException, MessageFormatException;

	/**
	 * Writes the message start line and headers to <tt>out</tt>.
	 */
	public abstract void write(OutputStream out) throws IOException;

	public final void setVersion(int major, int minor)
	{
		_majorVersion = major;
		_minorVersion = minor;
	}

	public final int getMajorVersion()
	{
		return _majorVersion;
	}

	public final int getMinorVersion()
	{
		return _minorVersion;
	}

	public final Headers getHeaders()
	{
		return _headers;
	}
	
	public final Headers getTrailer()
	{
		return _trailer;
	}
	
	public void clear()
	{
		setVersion(0, 0);
		_headers.clear();
		_trailer.clear();
	}
	
	/**
	 * Returns the usual positive/zero/negative integer
	 * if the message version is larger/equal/smaller
	 * than the supplied version.
	 */
	public int compareVersion(int major, int minor)
	{
		if(_majorVersion == major)
		{
			if(_minorVersion == minor)
				return 0;
			else
			if(_minorVersion < minor)
				return -1;
			else
				return 1;
		}
		else
		if(_majorVersion < major)
		{
			return -1;
		}
		else
		{
			return 1;
		}
	}
	
	/**
	 * Convenience method to find out the last transfer encoding
	 * applied to the message body, if any.
	 * 
	 * @return the lower-case name of the coding,
	 *         <tt>null</tt> if no coding is present
	 *         
	 */
	public final String lastTransferCoding()
	{
		String[] codings = _headers.getValueList("Transfer-Encoding");
		
		if(codings == null)
			return null;
			
		return codings[codings.length - 1].toLowerCase();
	}
	
	/**
	 * Returns <tt>true</tt> if the headers indicate
	 * that this message comes with a message body.
	 * 
	 * <b>Note:</b> any response to a HEAD request does NOT come with
	 *              a message body, even if the headers indicate this.
	 */
	public boolean hasBodyHeaders()
	{
		if
		(
			_headers.contains("Transfer-Encoding") ||
			_headers.contains("Content-Length") ||
			_headers.contains("Content-Type")
		)
			return true;
		else
			return false;
	}

	/**
	 * Reads a (CR)LF terminated line from <code>in</code>.
	 * 
	 * @return the line without terminating (CR)LF
	 */
	final String readLine(InputStream in) throws IOException
	{
		StringBuffer line = new StringBuffer(80);
		int i;
		char c;
		
		while(true)
		{
			if((i = in.read()) == -1)
				throw new IOException("Unexpected end of stream.");
			
			c = (char)(i & 0xff);
			if(c == '\r')
				; // ignore, as per RFC 2616
			else if(c == '\n')
				break;
			else
				line.append(c);
		}
		
		return line.toString();
	}
	
	/**
	 * Writes a CRLF terminated line to <code>out</code>.
	 */
	final void writeLine(String line, OutputStream out) throws IOException
	{
		out.write(line.getBytes("US-ASCII"));
		out.write(CRLF);
	}
}
