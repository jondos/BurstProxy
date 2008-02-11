/**
 * 
 */
package myproxy;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import myproxy.httpio.MessageFormatException;
import myproxy.httpio.Request;
import myproxy.httpio.Response;
import myproxy.httpio.URIFormatException;

/**
 * Handles a CONNECT (https) request
 * 
 * @author dh
 *
 */
public class ConnectRequestHandler extends AbstractRequestHandler implements RequestHandler {
		
	public ConnectRequestHandler(MyProxy controller, Handler handler) {
		super(controller, handler);
	}
	
	/**
	 * @throws HTTPException 
	 * @throws IOException 
	 * @see myproxy.RequestHandler#handleRequest(myproxy.Handler)
	 */
	public void handleRequest() throws IOException, HTTPException {
		
		// establish a *new* connection in any case; SSL connections cannot safely be reused.
		establishServerConnection();
		
		// in case we are forwarding, we have to kindly ask the remote server
		// before we notify the client that we are ready
		if(_forwardHostKey != null) {
			Request request;
			try {
				request = Request.createFromURI(_uri.getSource(), "CONNECT");
				request.getHeaders().put("Host", _uri.getSource());
			} catch (URIFormatException e) {
				// TODO should throw an HTTPException here
				throw new IOException(e.getMessage());
			}
			_server.write(request);
			
			Response response = new Response();
			try {
				_server.read(response);
			} catch (MessageFormatException e) {
				// TODO should throw an HTTPException here
				throw new IOException(e.getMessage());
			}
			if(! response.getStatusCode().equals("200"))
				throw new IOException("Cannot establish connection with remote server"+_uri.getSource()); // TODO should throw HTTPException here
		}
		
		Response res = new Response();
		res.setStatus("200", "Connection established");
		res.setVersion(1, 1);
		_client.write(res);
		
		_client.setTimeout(1);
		_server.setTimeout(1);
		
		byte[] buffer = new byte[65535];
		int read = 0;
		
		// this forwards data from client to server and from server to client alternatingly
		// performance might be increased by using two separate threads for relaying data
		try {
			while(true)
			{
				try {
					read = _client.in.read(buffer);
					if(read == -1) break;
					_server.out.write(buffer, 0, read);
					_server.out.flush();
					_client.setTimestamp();
					_server.setTimestamp();
				} catch (SocketTimeoutException timeout) {
					// this is okay!
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						// do nothing
					}
				}
				
				try {
					read = _server.in.read(buffer);
					if(read == -1) break;
					_client.out.write(buffer, 0, read);
					_client.out.flush();
					_client.setTimestamp();
					_server.setTimestamp();
				} catch (SocketTimeoutException timeout) {
					// this is okay!
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						// do nothing
					}
				}
			}
		} catch(IOException e) {
			_client.setKeepConnection(false);
			_server.setKeepConnection(false);
		}
	}
	
	public Connection establishServerConnection() throws IOException, HTTPException {
		String hostname;
		int port;
		
		// TODO: forwarding not tested yet
		if(_forwardHostKey == null)
		{
			hostname = getOriginServer();
			port = getOriginPort();
			
			StringBuffer tmp = new StringBuffer(hostname);
			tmp.append(':').append(port);
		}
		else
		{
			hostname = _controller.getForwardAddress().getHostName();
			port = _controller.getForwardAddress().getPort();
		}
		

		try
		{
			// experimental, DNS problem still happens
			InetAddress address = InetAddress.getByName(hostname);
			
			_server = new Connection(new Socket(address, port));
			_server.setTimeout(SERVER_COMM_TIMEOUT);
		}
		catch(UnknownHostException e)
		{
			throw new HTTPException("404", "err.unknownhost", hostname, _req.hasBodyHeaders());
		}
		catch(NoRouteToHostException e)
		{
			throw new HTTPException("502", "err.noroute", hostname, _req.hasBodyHeaders());
		}
		catch(ConnectException e)
		{
			StringBuffer detail = new StringBuffer(hostname);
			if(port != 80)
				detail.append(':').append(port);
			throw new HTTPException("502", "err.hostconnect", detail.toString(), _req.hasBodyHeaders());
		}
		catch(PortUnreachableException e)
		{
			throw new HTTPException("502", "err.portconnect", Integer.toString(port), _req.hasBodyHeaders());
		}
		
		return _server;
	}
}
