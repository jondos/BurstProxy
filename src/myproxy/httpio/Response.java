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
 * An HTTP response message (without body).
 */
public final class Response extends Message
{
	private String _statusCode, _reason;

	public void setStatus(String statusCode, String reason)
	{
		_statusCode = statusCode;
		_reason = reason;
	}
	
	public String getStatusCode()
	{
		return _statusCode;
	}
	
	public String getReason()
	{
		return _reason;
	}
	
	public void setReason(String reason)
	{
		_reason = reason;
	}
	
	public void clear()
	{
		super.clear();
		_statusCode = null;
		_reason = null;
	}
	
	/**
	 * Returns <tt>true</tt> if this response has a
	 * status code that MUST NOT come with a message body.
	 */
	public boolean isBodyless()
	{
		if
		(
			_statusCode.equals("304") ||
			_statusCode.equals("204") ||
			_statusCode.charAt(0) == '1'
		)
			return true;
		else			
			return false;
	}
	
	public void read(InputStream in) throws IOException, MessageFormatException
	{
		clear();
		
		String responseLine = readLine(in);
			
		StringTokenizer t = new StringTokenizer(responseLine, " ");
		if(t.countTokens() < 2)
			throw new MessageFormatException("Invalid response line: " + responseLine);
		
		String versionString = t.nextToken();
		Matcher httpVersion = HTTP_VERSION.matcher(versionString);
		if(!httpVersion.matches())
			throw new MessageFormatException("Unsupported protocol/version: " + versionString);
		setVersion(Integer.parseInt(httpVersion.group(1)), Integer.parseInt(httpVersion.group(2)));

		_statusCode =  t.nextToken();
		if(t.hasMoreTokens())
			_reason = t.nextToken("").trim();
		else
			_reason = null;

		getHeaders().read(in);
	}
	
	public void write(OutputStream out) throws IOException
	{
		StringBuffer responseLine = new StringBuffer(80);
		responseLine.append("HTTP/").append(getMajorVersion()).append('.').append(getMinorVersion());
		responseLine.append(' ').append(_statusCode);
		if(_reason != null)
			responseLine.append(' ').append(_reason);
		
		writeLine(responseLine.toString(), out);
		getHeaders().write(out);
	}

	public String toString()
	{
		StringBuffer value = new StringBuffer(80);
		
		value.append("HTTP/").append(getMajorVersion()).append('.').append(getMinorVersion());
		value.append(' ').append(_statusCode);
		if(_reason != null)
			value.append(' ').append(_reason);
		return value.toString();
	}
}
