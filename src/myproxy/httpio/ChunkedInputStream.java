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

import java.io.IOException;
import java.io.InputStream;

/**
 * Converts an HTTP <tt>chunked</tt> encoded
 * stream into a linear stream of bytes.
 */
public final class ChunkedInputStream extends InputStream
{
	private InputStream _in;
	private int _chunkSize, _bytesRead;
	private String _extensions;
	
	public ChunkedInputStream(InputStream in)
	{
		_in = in;
		_chunkSize = 0;
		_bytesRead = 0;
		_extensions = null;
	}

	public void startChunk() throws IOException
	{
		if(_bytesRead != _chunkSize)
			throw new IOException("Unfinished chunk.");
		
		_bytesRead = 0;
		
		String startLine = readLine();
		int pos = startLine.indexOf(';');
		if(pos == -1)
		{
			_chunkSize = Integer.parseInt(startLine.trim(), 16);
			_extensions = null;
		}
		else
		{
			_chunkSize = Integer.parseInt(startLine.substring(0, pos).trim(), 16);
			_extensions = startLine.substring(pos + 1).trim();
		}
	}
	
	public int available() throws IOException
	{
		return _in.available();
	}

	/**
	 * Clears the reference to the original stream.
	 */
	public void close()
	{
		_in = null;
		_chunkSize = 0;
		_bytesRead = 0;
		_extensions = null;
	}
		
	public int read() throws IOException
	{
		if(_bytesRead == _chunkSize)
			throw new IOException("Chunk finished.");
		
		int data = _in.read();
		
		if(data != -1)
		{
			_bytesRead++;
			if(_bytesRead == _chunkSize)
				readLine(); // discard trailing CRLF
		}
		
		return data;
	}
	
	public int read(byte[] b) throws IOException
	{
		return read(b, 0, b.length);
	}

	/**
	 * This method has been changed so that <code>length</code> never
	 * exceeds the return value of <code>chunkLeft()</code>.
	 */
	public int read(byte[] b, int offset, int length) throws IOException
	{
		if(_bytesRead == _chunkSize)
			throw new IOException("Chunk finished.");

		int read = _in.read(b, offset, (length > chunkLeft() ? chunkLeft() : length));
		
		//String buffer = new String(b);
		//System.out.print("!"+buffer+"!");
		
		if(read != -1)
		{
			_bytesRead += read;
			if(_bytesRead == _chunkSize)
				readLine(); // discard trailing CRLF
		}
		
		return read;
	}
	
	/**
	 * Returns the number of bytes in the current chunk.
	 * <tt>0</tt> means you hit the last chunk.
	 */
	public int chunkSize()
	{
		return _chunkSize;
	}
	
	/**
	 * Returns the number of bytes left to read from the current chunk.
	 */
	public int chunkLeft()
	{
		return _chunkSize - _bytesRead;
	}
	
	/**
	 * Verbosely returns the extensions on this chunk, or <tt>null</tt>.
	 */
	public String extensions()
	{
		return _extensions;
	}

	/**
	 * Reads a (CR)LF terminated line from <code>in</code>.
	 * 
	 * @return the line without terminating (CR)LF
	 */
	private String readLine() throws IOException
	{
		StringBuffer line = new StringBuffer(80);
		int i;
		char c;
		
		while(true)
		{
			if((i = _in.read()) == -1)
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
}
