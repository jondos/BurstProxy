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
import java.util.logging.*;

/**
 * A container for HTTP headers.
 */
public final class Headers
{
	private static final byte[] CRLF = { '\r', '\n' };
	private static final Pattern LIST_SEP = Pattern.compile("[ \\t]*,[ \\t]*");
	private static final Logger _logger = Logger.getLogger("myproxy.httpio");
	
	private final LinkedHashMap _map;
	private final List _cookies;
	
	public Headers()
	{
		_map = new LinkedHashMap();
		_cookies = new Vector();
	}

	/**
	 * Returns the complete header line for header <code>name</code>
	 * (case-insensitive), or <tt>null</tt> if it doesn't exist.
	 */
	public String get(String name)
	{
		return ((StringBuffer)_map.get(name.toLowerCase())).toString();
	}
	
	/**
	 * Returns only the header value for <code>name</code>, or <tt>null</tt>.
	 */
	public String getValue(String name)
	{
		StringBuffer header = (StringBuffer)_map.get(name.toLowerCase());
		
		if(header == null)
			return null;
			
		return header.substring(header.indexOf(": ") + 2);
	}
	
	/**
	 * Returns the list of comma-separated values for header <code>name</code>,
	 * or <tt>null</tt> if the header doesn't exist.
	 */
	public String[] getValueList(String name)
	{
		StringBuffer header = (StringBuffer)_map.get(name.toLowerCase());
		
		if(header == null)
			return null;
			
		return LIST_SEP.split(header.substring(header.indexOf(": ") + 2));
	}

	/**
	 * Stores header <code>name</code> with the assigned <code>value</code>.
	 * If <code>value</code> is <tt>null</tt>, the header will be removed.
	 */
	public void put(String name, String value)
	{
		if(value != null)
		{
			StringBuffer header = new StringBuffer(name.length() + value.length() + 2);
			header.append(name).append(": ").append(value);
			_map.put(name.toLowerCase(), header);
		}
		else
		{
			_map.remove(name.toLowerCase());
		}
	}
	
	/**
	 * Returns <tt>true</tt> if header <code>name</code> exists.
	 */
	public boolean contains(String name)
	{
		return _map.containsKey(name.toLowerCase());
	}
	
	/**
	 * Deletes all headers and cookies.
	 */
	public void clear()
	{
		_map.clear();
		_cookies.clear();
	}
	
	/**
	 * Returns a String iterator over all headers.
	 */
	public Iterator iterator()
	{
		final Iterator it = _map.values().iterator();
		
		return new Iterator() {
			public boolean hasNext()
			{
				return it.hasNext();
			}
			
			public Object next()
			{
				return it.next().toString();
			}
			
			public void remove()
			{
				it.remove();
			}
		};
	}
	
	/**
	 * Since the "Set-Cookie:" and "Cookie:" headers violate RFC 2616,
	 * they're stored separately.
	 * The returned list contains complete headerline strings.
	 */
	public List getCookies()
	{
		return _cookies;
	}
	
	/**
	 * Reads HTTP header lines from <tt>in</tt> until it hits an empty line.
	 */
	public void read(InputStream in) throws IOException
	{
		List   headerList = new Vector();
		String headerLine = readLine(in);
		
		while(headerLine.length() > 0)
		{
			headerList.add(headerLine);
			headerLine = readLine(in);
		}

		for(int i = 0; i < headerList.size(); i++)
		{
			headerLine = ((String)headerList.get(i)).trim();
			int pos = headerLine.indexOf(':');
			
			if(pos < 1)
			{
				_logger.warning("Invalid header line: " + headerLine);
				continue;
			}
			
			boolean emptyValue = (pos == headerLine.length() - 1);
			
			String headerName  = headerLine.substring(0, pos);
			String headerKey   = headerName.toLowerCase();
			String headerValue = headerLine.substring(pos + 1).trim();
			
			StringBuffer header;
			if(!_map.containsKey(headerKey))
			{
				// new header
				header = new StringBuffer(headerName.length() + headerValue.length() + 2);
				header.append(headerName).append(": ").append(headerValue);
			}
			else
			{
				// merge repeated header lines
				header = (StringBuffer)_map.get(headerKey);
				if(!emptyValue)
					header.append(", ").append(headerValue);
			}
			
			// collapse continued lines
			while(isLineIndented(headerList, i + 1))
			{
				header.append(' ').append(((String)headerList.get(i + 1)).trim());
				i++;
			}
			
			if(headerKey.equals("set-cookie") || headerKey.equals("cookie"))
			{
				if(!emptyValue)
					_cookies.add(header.toString());
			}
			else
			{
				_map.put(headerKey, header);
			}
		}
		
		headerList.clear();
	}

	/**
	 * A header line is indented (and thus a continuation)
	 * if it starts with spaces and/or tabs.
	 */
	private boolean isLineIndented(List stringList, int index)
	{
		if(stringList.size() > index)
		{
			String line = (String)stringList.get(index);
			
			if(line.charAt(0) == ' ' || line.charAt(0) == '\t')
				return true;
		}
		
		return false;
	}

	/**
	 * Writes HTTP message headers to <tt>out</tt>.
	 * 
	 * Writes all headers, the empty terminator line and flushes.
	 */
	public void write(OutputStream out) throws IOException
	{
		for(Iterator i = iterator(); i.hasNext();)
			writeLine((String)i.next(), out);
		for(Iterator i = _cookies.iterator(); i.hasNext();)
			writeLine((String)i.next(), out);
		out.write(CRLF);
		out.flush();
	}

	/**
	 * Reads a (CR)LF terminated line from <code>in</code>.
	 * 
	 * @return the line without terminating (CR)LF
	 */
	private String readLine(InputStream in) throws IOException
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
	private void writeLine(String line, OutputStream out) throws IOException
	{
		out.write(line.getBytes("US-ASCII"));
		out.write(CRLF);
	}
}
