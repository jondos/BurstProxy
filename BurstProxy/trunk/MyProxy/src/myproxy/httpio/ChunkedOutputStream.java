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
import java.io.OutputStream;

/**
 * Converts a byte stream into an HTTP <tt>chunked</tt> encoded stream.
 */
public final class ChunkedOutputStream extends OutputStream
{
	private static final byte[] CRLF = { '\r', '\n' };
	
	private OutputStream _out;
	private int _chunkSize, _bytesWritten;
	private String _extensions;
	
	public ChunkedOutputStream(OutputStream out)
	{
		_out = out;
		_chunkSize = 0;
		_bytesWritten = 0;
		_extensions = null;
	}
	
	public void startChunk(int size, String extensions) throws IOException
	{
		if(_bytesWritten != _chunkSize)
			throw new IOException("Unfinished chunk.");
			
		_chunkSize = size;
		_bytesWritten = 0;
		_extensions = extensions;
		_out.write(Integer.toHexString(size).getBytes("US-ASCII"));
		if(_extensions != null)
		{
			_out.write(';');
			_out.write(_extensions.getBytes("US-ASCII"));
		}
		_out.write(CRLF);
	}
	
	/**
	 * Writes the last chunk, flushes and
	 * clears the reference to the original stream.
	 */
	public void close() throws IOException
	{
		startChunk(0, null);
		_out.flush();
		_out = null;
	}
	
	public void flush() throws IOException
	{
		_out.flush();
	}
		
	public void write(int b) throws IOException
	{
		if(_bytesWritten == _chunkSize)
			throw new IOException("Chunk full.");

		_out.write(b);
		_bytesWritten++;
	}
	
	public void write(byte[] b) throws IOException
	{
		write(b, 0, b.length);
	}

	public void write(byte[] b, int offset, int length) throws IOException
	{
		if(length > chunkLeft())
			throw new IOException("Not enough bytes left in chunk.");
		
		_out.write(b, offset, length);
		_bytesWritten += length;
	}
	
	/**
	 * Returns the number of bytes left to write to the current chunk.
	 */
	public int chunkLeft()
	{
		return _chunkSize - _bytesWritten;
	}

	/**
	 * End chunk with CRLF (cf. RFC 2616 3.6.1:
	 *   chunk = chunk-size [ chunk-extension ] CRLF
     *           chunk-data CRLF
	 * @throws IOException 
	 */
	public void endChunk() throws IOException {
		_out.write(CRLF);
	}
}
