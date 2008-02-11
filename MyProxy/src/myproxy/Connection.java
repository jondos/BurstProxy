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
package myproxy;

import java.io.*;
import java.net.*;

import myproxy.httpio.*;

/**
 * Keeps one client or server connection. In addition to providing
 * direct access to buffered streams on the socket, the convenience
 * <code>read()</code> and <code>write()</code> methods perform
 * some services on the HTTP message headers.
 */
public final class Connection
{
	public final BufferedInputStream in;
	public final BufferedOutputStream out;

	private final Socket _socket;
	private boolean _keepConnection;
	private long _timestamp;
	
	public Connection(Socket socket) throws IOException
	{
		in = new BufferedInputStream(socket.getInputStream());
		out = new BufferedOutputStream(socket.getOutputStream());
		_socket = socket;
		_keepConnection = true;
		_timestamp = System.currentTimeMillis();
	}

	/**
	 * Returns <tt>true</tt> if the last message read did
	 * not contain a <tt>Connection: close</tt> header.
	 */
	public boolean keepConnection()
	{
		return _keepConnection;
	}
	
	/**
	 * Can be used to override the return value of
	 * <code>keepConnection()</code>.
	 */
	public void setKeepConnection(boolean keepConnection)
	{
		_keepConnection = keepConnection;
	}

	/**
	 * Returns the current socket timeout value.
	 * 
	 * @return the timeout value in milliseconds
	 */
	public int getTimeout() throws SocketException
	{
		return _socket.getSoTimeout();
	}
	
	/**
	 * Sets the socket timeout value.
	 */
	public void setTimeout(int milliSeconds) throws SocketException
	{
		_socket.setSoTimeout(milliSeconds);
	}
	
	/**
	 * Returns <tt>true</tt> if the socket considers itself closed.
	 */
	public boolean isClosed()
	{
		return _socket.isClosed();
	}
	
	/**
	 * Returns the time between the last call to <code>read()</code>,
	 * <code>write()</code> or <code>setTimestamp()</code> and now,
	 * in milliseconds.
	 */
	public long getIdleTime()
	{
		return System.currentTimeMillis() - _timestamp;
	}
	
	/**
	 * Sets the time of last activity to the current time.
	 */
	public void setTimestamp()
	{
		_timestamp = System.currentTimeMillis();
	}

	/**
	 * Reads an HTTP message from the socket.
	 * Additionally removes some non-standard headers and checks the
	 * <tt>Connection</tt> header for a <tt>close</tt> value.
	 */
	public void read(Message message) throws IOException, MessageFormatException
	{
		message.read(in);

		Headers headers = message.getHeaders();

		// remove evil headers
		headers.put("Proxy-Connection", null);
		headers.put("Keep-Alive", null);

		String[] connection = headers.getValueList("Connection");
		if(connection != null)
		{
			for(int i = 0; i < connection.length; i++)
			{
				if(connection[i].equalsIgnoreCase("close"))
					_keepConnection = false;
			}
		}
		
		setTimestamp();
	}

	/**
	 * Writes an HTTP message to the socket.
	 * Additionally removes most connection tokens specified
	 * in the <tt>Connection</tt> header and adds
	 * a <tt>Connection: close</tt> header according
	 * to the value of <code>keepConnection()</code>.
	 */
	public void write(Message message) throws IOException
	{
		Headers headers = message.getHeaders();

		// weed headers according to connection options
		String[] connection = headers.getValueList("Connection");
		if(connection != null)
		{
			for(int i = 0; i < connection.length; i++)
			{
				// transfer coding is passed on unmodified
				// (as long as it's "chunked"), so keep the header
				// TODO: fix "Connection" header handling
				// 1) class Handler knows better what to keep
				// 2) it's impossible to have tokens other than "close"
				if(!connection[i].equalsIgnoreCase("close") &&
				   !connection[i].equalsIgnoreCase("Transfer-Encoding"))
					headers.put(connection[i], null);
			}
		}
		
		if(_keepConnection)
			headers.put("Connection", null);
		else
			headers.put("Connection", "close");
		
		message.write(out);
		
		setTimestamp();
	}

	/**
	 * Closes the socket without throwing an <code>IOException</code>.
	 */
	public void safeClose()
	{
		try
		{
			_socket.close();
		}
		catch(IOException e)
		{
			// ignore
		}
	}

	/**
	 * Returns the remote socket's address in string form.
	 */
	public String toString()
	{
		if(_socket.getRemoteSocketAddress() != null)
			return _socket.getRemoteSocketAddress().toString();
		else
			return null;
	}
}
