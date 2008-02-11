/**
 * 
 */
package myproxy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

import myproxy.httpio.ChunkedOutputStream;
import myproxy.httpio.MessageFormatException;
import myproxy.httpio.Request;
import myproxy.httpio.Response;
import myproxy.httpio.URIFormatException;
import myproxy.httpio.URIParser;
import myproxy.prefetching.PrefetchUtils;
import myproxy.prefetching.PrefetchedEntity;
import myproxy.prefetching.PrefetchingParser;

/**
 * @author dh
 *
 */
public class RemotePrefetchRequestHandler extends AbstractRequestHandler implements RequestHandler {
		
	private int nextPrefetchingHandlerId;


	public RemotePrefetchRequestHandler(MyProxy controller, Handler handler) {
		super(controller, handler);
	}

	public void handleRequest() throws IOException, HTTPException, MessageFormatException {
		
		_logger.finer(getName() + " incoming request for "+_uri.getSource());
		
		if(requestIsBlocked())
		{
			if(_req.hasBodyHeaders())
				_client.setKeepConnection(false);
			return;
		}
		
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
		
		// change Request-URI to "abs_path" format, unless we're forwarding
		if(_controller.getForwardAddress() == null)
			_req.setURI(_uri.getPathSource());
		
		try
		{
			_server.write(_req);
		}
		catch(SocketTimeoutException e)
		{
			throw new HTTPException("504", "err.servertimeout", null, _req.hasBodyHeaders());
		}

		if(_msgLogger.isLoggable(Level.FINER))
			logHTTPMessage(getName() + " request header sent to server "+_server.toString(), _req);
		
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


		// TODO this might break 100 continue responses,have to check into that

		try {
			prefetchEntities(baseURI);
		} catch (URIFormatException e) {
			throw new MessageFormatException(e.getMessage());
		}

		/*
		} else {
			// loop over any remaining 1xx responses
			do
			{
				_server.read(_res);
		
				if(!hasKnownTransferCoding(_res))
				{
					_logger.logp(Level.WARNING, _handler.getName(), "handleRegular", "Unknown \"Transfer-Encoding\" from server: " + _res.lastTransferCoding());
					_server.safeClose();
					throw new HTTPException("500", "err.servercoding", _res.lastTransferCoding(), false);
				}
				
				// remove/modify response headers as necessary
				touchResponseHeaders();
				_client.write(_res);

				if(_msgLogger.isLoggable(Level.FINER))
					logHTTPMessage("Response header sent to client.", _res);
			}
			while(_res.getStatusCode().charAt(0) == '1');
			

			sendResponseBody();
			if(!_server.keepConnection() || _res.compareVersion(1, 1) < 0)
				_server.safeClose();
		}
		*/
	}

	
	/** retrieve response body from server, find all embedded entities,
	 * prefetch them and prepare for transmission to client.
	 * 
	 * @param  baseURI	The URL of the page which contains the prefetched entities
	 * 
	 * @throws MessageFormatException 
	 * @throws IOException 
	 * @throws URIFormatException 
	 */
	private void prefetchEntities(URIParser baseURI)
	throws IOException, HTTPException, MessageFormatException, URIFormatException
	{
		PrefetchingHandler ph;
		ByteArrayOutputStream headersOut;
		ByteArrayInputStream headersIn;
		Request req;
		String prefetchStrategy = _reqHeaders.get("X-Accept-Prefetching");
		boolean doCompressHeaders = prefetchStrategy.contains("HE=gzip");
		boolean doCompressBody = prefetchStrategy.contains("BE=gzip");
		
		// send initial response for local end
		Response initialResponse = new Response();
		initialResponse.setVersion(1,1);
		initialResponse.setStatus("200", "OK");
		initialResponse.getHeaders().put("X-Prefetch-Strategy", "toptobottom");
		_client.write(initialResponse);
		
		
		// prefetch the request itself
		
		// fetch headers from server
		_server.read(_res);
		
		
		// prepare request for embedded entities
		req = Request.createFromURI(baseURI.getSource());
		
		
		// copy headers
		headersOut  = new ByteArrayOutputStream();

		// remove prefetching header
		_reqHeaders.put("X-Accept-Prefetching", null);
		
		
		_reqHeaders.write(headersOut);
		headersIn = new ByteArrayInputStream(headersOut.toByteArray());
		req.getHeaders().read(headersIn);
		
		
		// retrieve body from server
		PrefetchedEntity websiteEntity = new PrefetchedEntity(req);
		websiteEntity.setResponse(_res);
		ph = new PrefetchingHandler(_controller, getName(), websiteEntity, nextPrefetchingHandlerId++);
		ph.setServerConnection(_server); // server connection has already been established by caller -> use it!
		ph.prefetchEntityBody();

		// send main response back to client
		byte[] responseBodyBuffer = websiteEntity.getBuffer();
		
		
		_res.getHeaders().put("Content-Length", String.valueOf(responseBodyBuffer.length));
		_res.getHeaders().put("Transfer-Encoding", null);
		
		ByteArrayOutputStream entityHeader = new ByteArrayOutputStream();
		_res.write(entityHeader);
		
		if(doCompressHeaders) {
			entityHeader = compressHeadersGZIP(entityHeader);
		}
		
		int entityHeaderSize = entityHeader.size();
		
		_logger.finer(getName() + " sending response header and body to local end");

		ChunkedOutputStream clientChunkedOutputStream = new ChunkedOutputStream(_client.out);
		String extension;

		extension="type=response; header-length="+entityHeaderSize;
		if(doCompressHeaders) {
			extension+=";HE=gzip";
		}

		
		String contentType=ph.getEntity().getResponse().getHeaders().getValue("Content-Type");
		String contentEncoding=ph.getEntity().getResponse().getHeaders().getValue("Content-Encoding");
		
		// compress body if it is not compressed already
		if(doCompressBody && contentType!=null && 
				contentType.matches(".*?/html.*|.*?/xml.*|/.*?xhtml.*|.*?css.*|.*?x-javascript.*") &&
				(contentEncoding == null || !contentEncoding.equals("gzip"))) {
			extension+=";BE=gzip";
			responseBodyBuffer = compressData(responseBodyBuffer);
		}
		
		clientChunkedOutputStream.startChunk(entityHeaderSize+responseBodyBuffer.length, extension);
		copyStream(new ByteArrayInputStream(entityHeader.toByteArray()), clientChunkedOutputStream, entityHeaderSize);
		copyStream(new ByteArrayInputStream(responseBodyBuffer), clientChunkedOutputStream, responseBodyBuffer.length);
		clientChunkedOutputStream.endChunk();
		clientChunkedOutputStream.flush();
		
		if(!_server.keepConnection() || _res.compareVersion(1, 1) < 0)
			_server.safeClose();
		
		// search for embedded objects
		boolean searchForEmbeddedElements=false;
		PrefetchingParser parser = null;
		List urlsOfEmbeddedEntities = null;
		
		if(contentType!=null) {
			if(contentType.matches(".*?/html.*|.*?/xml.*|/.*?xhtml.*")) {
				searchForEmbeddedElements=true;
				parser = PrefetchUtils.getHTMLParser();
			}
			else if(contentType.matches((".*?/css.*"))) {
				searchForEmbeddedElements=true;
				parser = PrefetchUtils.getCSSParser();
			}
			else
				_logger.finest("NOTHING TO PREFETCH FOR "+ph.getEntity().getRequest().getFullURIPath());
		} else {
			// for bodyless responses (e.g. 304 Not Modified)
			searchForEmbeddedElements=false;
		}
		
		if(!searchForEmbeddedElements) {
			clientChunkedOutputStream.close();
			return;
		}
		
		
		// send initial response to local end containing details about the prefetched URLs
		if(searchForEmbeddedElements) {
			byte[] responseBodyBufferUncompressed = websiteEntity.getBufferUncompressed();
			String responseBody = new String(responseBodyBufferUncompressed);
			urlsOfEmbeddedEntities = parser.findURLsInResponse(baseURI, responseBody);
			
			Iterator urlIterator = urlsOfEmbeddedEntities.iterator();
			String urllistResponseBody="";
			while(urlIterator.hasNext()) {
				urllistResponseBody+=urlIterator.next()+"\n";
			}
			
			byte [] urllistByteArray = urllistResponseBody.getBytes();
			
			urllistByteArray = compressData(urllistByteArray);
			
			extension="type=urllist;BE=gzip";
			clientChunkedOutputStream.startChunk(urllistByteArray.length, extension);
			clientChunkedOutputStream.write(urllistByteArray);
			clientChunkedOutputStream.endChunk();
			clientChunkedOutputStream.flush();
			
			// create new PrefetchedEntities and start prefetching them
			// for now, create 1 thread for each embedded entity -- this may consume a lot of resources, though
			
			// TODO: use thread pool pattern properly
			// cf. http://www.ibm.com/developerworks/library/j-jtp0730.html
			// and http://en.wikipedia.org/wiki/Thread_pool_pattern
			
			ArrayList entityHandlers = new ArrayList();
			urlIterator = urlsOfEmbeddedEntities.iterator();
			
			while(urlIterator.hasNext()) {
				String uri = (String)urlIterator.next();
				URIParser currentURI = new URIParser();
				currentURI.parse(uri);
				
				// prepare request
				Request r;
				try {
					r = Request.createFromURI(uri);
					
					// copy headers
					headersOut = new ByteArrayOutputStream();
					_reqHeaders.write(headersOut);
					headersIn = new ByteArrayInputStream(headersOut.toByteArray());
					r.getHeaders().read(headersIn);
					r.getHeaders().put("Referer", baseURI.getSource());
					r.getHeaders().put("Host", currentURI.getHost());
					
					PrefetchedEntity pe = new PrefetchedEntity(r);
					
					_logger.finer(getName() + " prefetching URL "+uri);
	
					ph = new PrefetchingHandler(_controller, getName(), pe, nextPrefetchingHandlerId++);
					
					entityHandlers.add(ph);
					_controller.work(ph);
					//new Thread(ph).start();
				} catch (URIFormatException e) {
					e.printStackTrace();
				}
			}
			
			// now wait until all prefetched objects have been retrieved
			_logger.finer(getName() + " waiting for all prefetched pending requests to complete");
			while(true) {
				boolean allDone = true;
				for(int i=0;i<entityHandlers.size();i++) {
					if( ((PrefetchingHandler)entityHandlers.get(i)).isCompleted() == false)
						allDone = false;
				}
				if(allDone) break;
				try {
					Thread.sleep(20);
				} catch (InterruptedException e) {}
			}
			
			
			// now all prefetched entities are available for transmission to the client
			for(int i=0;i<entityHandlers.size();i++) {
				PrefetchingHandler entityHandler = (PrefetchingHandler) entityHandlers.get(i);
				_logger.finer(entityHandler.getName() + " sending reply to client");
				
				entityHandler.getEntity().getResponse().getHeaders().put("Transfer-Encoding", null);
				entityHandler.getEntity().getResponse().getHeaders().put("Content-Length", String.valueOf(entityHandler.getEntity().getBuffer().length));

				entityHeader = new ByteArrayOutputStream();
				entityHandler.getEntity().getResponse().write(entityHeader);


				if(doCompressHeaders) {
					entityHeader = compressHeadersGZIP(entityHeader);
				}
				
				entityHeaderSize = entityHeader.size();

				extension="type=prefetched; url="+i+"; header-length="+entityHeaderSize;
				if(doCompressHeaders) {
					extension+=";HE=gzip";
				}
				
				contentType=entityHandler.getEntity().getResponse().getHeaders().getValue("Content-Type");
				contentEncoding=entityHandler.getEntity().getResponse().getHeaders().getValue("Content-Encoding");
				
				// compress body if it is not compressed already
				responseBodyBuffer = entityHandler.getEntity().getBuffer();
				if(doCompressBody && contentType!=null && 
						contentType.matches(".*?/html.*|.*?/xml.*|/.*?xhtml.*|.*?css.*|.*?x-javascript.*") &&
						(contentEncoding == null || !contentEncoding.equals("gzip"))) {
					extension+=";BE=gzip";
					responseBodyBuffer = compressData(responseBodyBuffer);
				}

				
				clientChunkedOutputStream.startChunk(entityHeaderSize+responseBodyBuffer.length, extension);
				copyStream(new ByteArrayInputStream(entityHeader.toByteArray()), clientChunkedOutputStream, entityHeaderSize);
				copyStream(new ByteArrayInputStream(responseBodyBuffer), clientChunkedOutputStream, -1);
				clientChunkedOutputStream.endChunk();
			}
			clientChunkedOutputStream.close();
			_logger.finer(getName() + " all prefetched entities have been sent.");
		}
		//_client.safeClose();
	}

	/**
	 * compress data with GZIP
	 * 
	 * @param data
	 * @throws IOException
	 */
	private byte[] compressData(byte [] data ) throws IOException {
		ByteArrayOutputStream compressedEntityHeader = new ByteArrayOutputStream();
		GZIPOutputStream compressedOutputStream= new GZIPOutputStream(compressedEntityHeader);
		compressedOutputStream.write(data);
		compressedOutputStream.close();
		return compressedEntityHeader.toByteArray();
	}

	/**
	 * compresses headers with GZIP
	 * 
	 * @param entityHeader
	 * @return 
	 * @throws IOException
	 */
	private ByteArrayOutputStream compressHeadersGZIP(ByteArrayOutputStream entityHeader) throws IOException {
		ByteArrayOutputStream compressedEntityHeader = new ByteArrayOutputStream();
		GZIPOutputStream compressedOutputStream= new GZIPOutputStream(compressedEntityHeader);
		compressedOutputStream.write(entityHeader.toByteArray());
		compressedOutputStream.close();
		return compressedEntityHeader;
		
	}
}
