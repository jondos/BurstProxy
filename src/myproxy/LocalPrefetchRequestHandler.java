/**
 * 
 */
package myproxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


import myproxy.httpio.ChunkExtensions;
import myproxy.httpio.ChunkedInputStream;
import myproxy.httpio.MessageFormatException;
import myproxy.httpio.Request;
import myproxy.httpio.Response;
import myproxy.httpio.URIFormatException;
import myproxy.httpio.URIParser;
import myproxy.prefetching.PrefetchedEntity;
import myproxy.prefetching.PrefetchedEntityStore;

/**
 * @author dh
 *
 */
public class LocalPrefetchRequestHandler extends AbstractRequestHandler
		implements RequestHandler {
	
	// how long should we wait for the prefetched entities before we abort the prefetching?
	private static final long MAX_WAIT_TIME = 20 * 1000;
	private int nextPrefetchingHandlerId;
	
	public LocalPrefetchRequestHandler(MyProxy controller, Handler handler) {
		super(controller, handler);		
	}

	/* (non-Javadoc)
	 * @see myproxy.RequestHandler#handleRequest()
	 */
	public void handleRequest() throws IOException, HTTPException,
			MessageFormatException {

		_logger.finer(getName() + " incoming request for "+_uri.getSource());
		
		if(requestIsBlocked())
		{
			if(_req.hasBodyHeaders())
				_client.setKeepConnection(false);
			return;
		}
		
		PrefetchedEntityStore entityStore = PrefetchedEntityStore.getInstance();
		
		// fortunately, we know about embedded entities even before the browser can request them
		if(entityStore.containsURL(_req.getURI().getSource())) {
			handleAlreadyPrefetchedEntity(_req.getURI().getSource());
			return;
		}
		
	
		// add prefetch header so that remote end actually will perform prefetching
		_reqHeaders.put("X-Accept-Prefetching", "toptobottom"); //HE=gzip,BE=gzip
		//_reqHeaders.put("Accept-Encoding", "");		
		_reqHeaders.put("Connection", "close");
		boolean expectContinue = false;
		
		// check for 100-continue
		String[] expect = _reqHeaders.getValueList("Expect");
		if(expect != null)
		{
			for(int i = 0; i < expect.length; i++)
			{
				if(expect[i].equalsIgnoreCase("100-continue"))
				{
					expectContinue = true;
				}
				else
				{
					_logger.logp(Level.WARNING, getName(), "handleRegular", "Unknown \"Expect\" extension: " + expect[i]);
					throw new HTTPException("417", "err.expectext", expect[i], _req.hasBodyHeaders());
				}
			}
		}
		
		getServerConnection();
		
		// adjust the Max-Forwards header, if necessary
		if(_req.getMethod().equals("OPTIONS") || _req.getMethod().equals("TRACE"))
		{
			String value = _reqHeaders.getValue("Max-Forwards");
			if(value != null)
			{
				try
				{
					_reqHeaders.put("Max-Forwards", Integer.toString(Integer.parseInt(value) - 1));
				}
				catch(NumberFormatException e)
				{
					throw new HTTPException("400", "err.maxforwards", value, _req.hasBodyHeaders());
				}
			}
		}
		
		// remove/modify request headers as necessary
		touchRequestHeaders();
		
		// keep original request URI (will be overwritten by next statements)
		URIParser baseURI = new URIParser();
		try {
			baseURI.parse(_req.getURI().getSource());
		} catch(URIFormatException e) {
			throw new MessageFormatException(e.getMessage());
		}
				
					
		// send request off to remote end
		try
		{
			_server.write(_req);
		}
		catch(SocketTimeoutException e)
		{
			throw new HTTPException("504", "err.servertimeout", null, _req.hasBodyHeaders());
		}

		if(_msgLogger.isLoggable(Level.FINER))
			logHTTPMessage(getName() +" request header sent to remote end; remote server: "+_server.toString(), _req);
		
		
		if(expectContinue)
		{
			_server.read(_res);
	
			if(!hasKnownTransferCoding(_res))
			{
				_logger.logp(Level.WARNING, getName(), "handleRegular", "Unknown \"Transfer-Encoding\" from server: " + _res.lastTransferCoding());
				_server.safeClose();
				throw new HTTPException("500", "err.servercoding", _res.lastTransferCoding(), false);
			}
			
			// remove/modify response headers as necessary
			touchResponseHeaders();
			_client.write(_res);
			
			if(!_res.getStatusCode().equals("100"))
			{
				sendResponseBody();
				if(!_server.keepConnection())
					_server.safeClose();
				return;
			}
		}
		
		// copy request body, if any
		String transferCoding = _req.lastTransferCoding();
		if(transferCoding != null)
		{
			if(transferCoding.equalsIgnoreCase("chunked"))
			{
				copyChunks(_client.in, _server.out);
				_req.getTrailer().read(_client.in);
				_req.getTrailer().write(_server.out);
				_server.setTimestamp();
			}
			else
			{
				throw new HTTPException("400", "err.clientcoding", transferCoding, true);
			}
		}
		else if(_reqHeaders.contains("Content-Length"))
		{
			try
			{
				copyStream(_client.in, _server.out, Integer.parseInt(_reqHeaders.getValue("Content-Length")));
				_server.setTimestamp();
			}
			catch(NumberFormatException e)
			{
				throw new HTTPException("400", "err.badcontentlength", _reqHeaders.getValue("Content-Length"), true);
			}
		}
		
		
		// read initial response from remote end
		Response initialResponse = new Response();
		_server.read(initialResponse);
		
		if(!initialResponse.getStatusCode().equals("200"))
			throw new HTTPException("500", "err.remoteenderror", initialResponse.getReason(), false);
		
		String prefetchStrategy=initialResponse.getHeaders().get("X-Prefetch-Strategy");
		if(prefetchStrategy==null)
			throw new HTTPException("500", "err.remoteenderror", "X-Prefetch-Strategy header is missing", false);
		
		ChunkedInputStream serverChunkedInput = new ChunkedInputStream(_server.in);
		String extString;
		ChunkExtensions extensions;
		int headerLength;
		ByteArrayOutputStream buffer;
		
		serverChunkedInput.startChunk();
		extString=serverChunkedInput.extensions();
		extensions = new ChunkExtensions(extString); 
		
		boolean doUncompressHeaders = (extensions.getHeaderEncoding() != null && extensions.getHeaderEncoding().equals("gzip"));
		boolean doUncompressBody = (extensions.getBodyEncoding() != null && extensions.getBodyEncoding().equals("gzip"));
		
		if(!extensions.getType().equals("response"))
			throw new HTTPException("500", "err.remoteenderror", "Chunk with type=response expected, but received type "+extensions.getType()+" instead", false);
		
		try {
			headerLength=extensions.getHeaderLength();
		} catch(NumberFormatException e) {
			throw new HTTPException("500", "err.remoteenderror", "Invalid or missing header-length chunk-extension", false);
		}
		
		buffer = new ByteArrayOutputStream(headerLength);
		copyStream(serverChunkedInput, buffer, headerLength);
		
		if(doUncompressHeaders) {
			buffer = uncompressHeadersGZIP(buffer);
		}
		

		_res.read(new ByteArrayInputStream(buffer.toByteArray()));
		
		if(_msgLogger.isLoggable(Level.FINER))
			logHTTPMessage(getName()+ " response header received from remote end; remote server: "+_server.toString(), _res);

		
		if(!hasKnownTransferCoding(_res))
		{
			_logger.logp(Level.WARNING, getName(), "handleRegular", "Unknown \"Transfer-Encoding\" from server: " + _res.lastTransferCoding());
			_server.safeClose();
			throw new HTTPException("500", "err.servercoding", _res.lastTransferCoding(), false);
		}

		// remove/modify response headers as necessary
		touchResponseHeaders();


		// BEGIN PREFETCH SPECIFIC CODE 
		PrefetchingHandler ph;
		ByteArrayOutputStream headersOut;
		ByteArrayInputStream headersIn;
		Request req;
		
		// prefetch the main page itself
		
		// prepare request -> reuse existing one
		req = _req;

		// copy headers
		headersOut  = new ByteArrayOutputStream();
		
		_reqHeaders.write(headersOut);
		headersIn = new ByteArrayInputStream(headersOut.toByteArray());
		req.getHeaders().read(headersIn);
		
		// remove prefetching header
		req.getHeaders().put("Prefetch-Strategy", null);
		
		// retrieve body
		_logger.finer(getName() + " recv response body from remote end");
		
		PrefetchedEntity websiteEntity = new PrefetchedEntity(req);
		websiteEntity.setResponse(_res);
		ph = new PrefetchingHandler(_controller, getName(), websiteEntity, nextPrefetchingHandlerId++);
		ph.setServerConnection(_server); // server connection has already been established by caller -> use it!
		
		if(doUncompressBody) {
			ByteArrayOutputStream compressedBuffer = new ByteArrayOutputStream(serverChunkedInput.chunkLeft());
			copyStream(serverChunkedInput, compressedBuffer, serverChunkedInput.chunkLeft());
			ph.prefetchEntityBody(new ByteArrayInputStream(uncompressData(compressedBuffer.toByteArray())));
		} else {
			ph.prefetchEntityBody(serverChunkedInput);
		}
		
		int bodyContentLength = Integer.parseInt(_res.getHeaders().getValue("Content-Length"));
		
		// send main response back to client
		_res.getHeaders().put("Content-Length", String.valueOf(bodyContentLength)); //TODO ???
		_res.getHeaders().put("Transfer-Encoding", null);
		
		// have to close connection because we will wait for the prefetched responses from the server below
		// and cannot deal with potential pipelined subrequests from the client.
		// TODO: Have to check whether we can do it this way (conforming to standard?)
		_res.getHeaders().put("Connection", "close");
		
		_client.write(_res);
		
		if(_msgLogger.isLoggable(Level.FINER))
			logHTTPMessage(getName() + " response header sent to client.", _res);
		

		List urlsOfEmbeddedEntities = new ArrayList();
		
		serverChunkedInput.startChunk();
		if(serverChunkedInput.chunkSize()>0) {
			extString=serverChunkedInput.extensions();
			extensions = new ChunkExtensions(extString); 
			
			if(!extensions.getType().equals("urllist"))
				throw new HTTPException("500", "err.remoteenderror", "Chunk with type=urllist expected, but received type "+extensions.getType()+" instead", false);
			
			headerLength=0;
			buffer = new ByteArrayOutputStream(headerLength);
			copyStream(serverChunkedInput, buffer, serverChunkedInput.chunkSize());
			
			String urlString = new String(uncompressData(buffer.toByteArray()));
			StringTokenizer tok = new StringTokenizer(urlString);
			
			while(tok.hasMoreTokens()) {
				String url = tok.nextToken("\n");
				urlsOfEmbeddedEntities.add(url);
				
				if(!entityStore.containsURL(url)) {
					entityStore.prepareForStorage(url);
				}
			}
		}
		
		
		
		// IMPORTANT: Do not send the body to the client before we have parsed the HTML for links
		// otherwise client might send subsequent requests before we know which links will be prefetched
		
		_logger.finer(getName() + " send response body to client");
		
		// send only as many bytes as specified by the content-length header
		// reason: directly behind the first document the embedded entities might already
		// be present - and we certainly do not want to send them to the client along with the html!
		copyStream(new ByteArrayInputStream(websiteEntity.getBuffer()),
				_client.out, bodyContentLength);
		
		// close it down - might violate RFC?
		_client.safeClose();
		
		if(urlsOfEmbeddedEntities.size()>0) {
			// keep the connection to the remote end open and wait for the prefetched blob;
			// with toptobottom strategy the order of the prefetched elements is predefined
			// just add them one after another. 
			boolean waitCondition = true;
			long lastReceivedTime = System.currentTimeMillis();
			
			_logger.finer(getName() + " waiting for embedded entities from remote end...");
			while(waitCondition) {
				if(serverChunkedInput.available()>0) {
					lastReceivedTime = System.currentTimeMillis();
					Response response = new Response();
					
					serverChunkedInput.startChunk();
					
					// is this the end? (denoted by a "0")
					if(serverChunkedInput.chunkSize()==0 && serverChunkedInput.extensions()==null) {
						_logger.finer(getName() +" all chunks have been received");
						break;
					}
						
					
					extString = serverChunkedInput.extensions();
					extensions = new ChunkExtensions(extString);
					if(!extensions.getType().equals("prefetched"))
						throw new HTTPException("500", "err.remoteenderror", "Chunk with type=prefetched expected, but received type "+extensions.getType()+" instead", false);
					
					doUncompressHeaders = (extensions.getHeaderEncoding() != null && extensions.getHeaderEncoding().equals("gzip"));
					doUncompressBody = (extensions.getBodyEncoding() != null && extensions.getBodyEncoding().equals("gzip"));

					headerLength=extensions.getHeaderLength();
					buffer = new ByteArrayOutputStream(headerLength);
					copyStream(serverChunkedInput, buffer, headerLength);
					
					if(doUncompressHeaders) {
						buffer = uncompressHeadersGZIP(buffer);
					}
					
					response.read(new ByteArrayInputStream(buffer.toByteArray()));
				
					if(!hasKnownTransferCoding(response))
					{
						_logger.logp(Level.WARNING, getName(), "handleRegular", "Unknown \"Transfer-Encoding\" from server: " + response.lastTransferCoding());
						_server.safeClose();
						throw new HTTPException("500", "err.servercoding", response.lastTransferCoding(), false);
					}
					
					if(_msgLogger.isLoggable(Level.FINER))
						logHTTPMessage(getName()+" prefetched response header received from remote end", response);

					_logger.finer(getName() + " recv response body from remote end");
					websiteEntity = new PrefetchedEntity();
					websiteEntity.setResponse(response);
					ph = new PrefetchingHandler(_controller, getName(), websiteEntity, nextPrefetchingHandlerId++);
					ph.setServerConnection(_server); // server connection has already been established by caller -> use it!
					
					if(doUncompressBody) {
						ByteArrayOutputStream compressedBuffer = new ByteArrayOutputStream(serverChunkedInput.chunkLeft());
						copyStream(serverChunkedInput, compressedBuffer, serverChunkedInput.chunkLeft());
						ph.prefetchEntityBody(new ByteArrayInputStream(uncompressData(compressedBuffer.toByteArray())));
					} else {
						ph.prefetchEntityBody(serverChunkedInput);
					}
					
					websiteEntity.setCompleted(true); // set completedAt timestamp so that it can be purged automatically later
	
					//_res.getHeaders().put("Content-Length", String.valueOf(websiteEntity.getBuffer().length));
					//_res.getHeaders().put("Transfer-Encoding", null);
					
					// store response for later use
					String url = (String)extensions.getUrl();
					int urlIndex;
					try {
						urlIndex=Integer.parseInt(url);
						url = (String)urlsOfEmbeddedEntities.get(urlIndex);
					} catch(NumberFormatException e) { ; }
					
					websiteEntity.setURI(url);
					
					_logger.finer(getName() + " storing prefetched entity "+url);
					
					// TODO: is this multi-threading compatible? needs synchronized?
					entityStore.store(url, websiteEntity);	
				} else {
					try { Thread.sleep(10); } catch (InterruptedException e) { }
				}
			
				if( (System.currentTimeMillis()-lastReceivedTime) > MAX_WAIT_TIME) {
					_logger.log(Level.WARNING, "Time limit for prefetching was reached; aborting.");
					waitCondition=false;
					_server.safeClose();
					continue;
				}
			}
		} // end searchForEmbeddedElements
		
		// close connection to remote end when the blob has been received completely
		// TODO: does this work?
		//if(!_server.keepConnection() || _res.compareVersion(1, 1) < 0)
		_server.safeClose();
	}

	private void handleAlreadyPrefetchedEntity(String url) throws IOException {
		_logger.finer(getName() + " client requested prefetched entity: "+_req.getURI().getSource());
		
		PrefetchedEntityStore entityStore = PrefetchedEntityStore.getInstance();  
		
		if(!entityStore.containsURL(url))
			throw new IOException("Error - cannot find prefetched entity for URL "+url);
		
		PrefetchedEntity pe;
		
		boolean waitCondition = true;
		
		long startTime = System.currentTimeMillis();
		
		// stall the client as long as the prefetched entity has not been retrieved
		do {
			if((pe = (PrefetchedEntity) entityStore.getEntity(url)) != null) {
				_logger.finer(getName() + " prefetched entity has been received; sending it to client");
				pe.getResponse().getHeaders().put("Connection", "close"); // hang up because we can't deal with Keep-Alive so far
				_client.write(pe.getResponse());
				copyStream(new ByteArrayInputStream(pe.getBuffer()), _client.out, pe.getBuffer().length);
				waitCondition=false;
				_client.safeClose();
			} else {
				try { Thread.sleep(20); } catch(InterruptedException e) {}
				if( (System.currentTimeMillis() - startTime) > MAX_WAIT_TIME) {
					// TODO: and what are we going to do with the request now?
					// send it towards the remote end again, tell the client, or what?
					waitCondition=false;
					_logger.finer(getName() + " prefetching of entity has timed out - what should I do?");
					_client.safeClose();
				}
			}
		} while(waitCondition);
	}
	
	public void getServerConnection() throws IOException, HTTPException {
		String hostname;
		int port;
		String key;
		
		String prefetchingRemoteEndHostname = _controller.getPrefetchingRemoteAddress().getHostName();
		int prefetchingRemoteEndPort        = _controller.getPrefetchingRemoteAddress().getPort();
		
		// TODO: forwarding not tested yet
		if(_forwardHostKey == null)
		{
			hostname = prefetchingRemoteEndHostname;
			port = prefetchingRemoteEndPort;
			
			StringBuffer tmp = new StringBuffer(hostname);
			tmp.append(':').append(port);
			
			key = tmp.toString();
		}
		else
		{
			hostname = _controller.getForwardAddress().getHostName();
			port = _controller.getForwardAddress().getPort();
			
			key = _forwardHostKey;
			
		}
		
		// TODO implement persistent connectins like in abstractrequesthandler
		
		try
		{
			// experimental, DNS problem still happens
			InetAddress address = InetAddress.getByName(hostname);
			
			_server = new Connection(new Socket(address, port));
			_server.setTimeout(SERVER_COMM_TIMEOUT);
			
			// send a CONNECT Request, if this connection is forwarded over a proxy
			if(_forwardHostKey != null) {
				Request connectRequest = new Request();
				connectRequest.setMethod("CONNECT");
				connectRequest.setVersion(1, 1);
				connectRequest.setURI("https://"+prefetchingRemoteEndHostname+":"+prefetchingRemoteEndPort);
				
				Response connectResponse = new Response();
				
				_server.write(connectRequest);
				_server.read(connectResponse);
				
				if(connectResponse.getStatusCode()!="200") {
					throw new HTTPException("502", "err.forwarderrefusedremoteend", hostname, _req.hasBodyHeaders());
				}
			}
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
		} catch (MessageFormatException e)
		{
			throw new HTTPException("502", "err.invalidremoteend", hostname, _req.hasBodyHeaders()); 
		}
	}
	
	/**
	 * compress data with GZIP
	 * 
	 * @param data
	 * @throws IOException
	 */
	private byte[] uncompressData(byte [] data ) throws IOException {
		ByteArrayInputStream compressedEntityHeader = new ByteArrayInputStream(data);
		GZIPInputStream uncompressedInputStream= new GZIPInputStream(compressedEntityHeader);
		
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		int read;
		while( (read = uncompressedInputStream.read(buf)) != -1) {
			result.write(buf,0,read);
		}
		uncompressedInputStream.close();
		return result.toByteArray();
	}

	/**
	 * compresses headers with GZIP
	 * 
	 * @param entityHeader
	 * @return 
	 * @throws IOException
	 */
	private ByteArrayOutputStream uncompressHeadersGZIP(ByteArrayOutputStream entityHeader) throws IOException {
		ByteArrayInputStream compressedEntityHeader = new ByteArrayInputStream(entityHeader.toByteArray());
		GZIPInputStream uncompressedInputStream= new GZIPInputStream(compressedEntityHeader);
		ByteArrayOutputStream result = new ByteArrayOutputStream();
		
		byte[] buf = new byte[4096];
		int read;
		while( (read = uncompressedInputStream.read(buf)) != -1) {
			result.write(buf,0,read);
		}
		uncompressedInputStream.close();
		return result;
	}
}

