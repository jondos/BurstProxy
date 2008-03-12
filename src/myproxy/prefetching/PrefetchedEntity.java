package myproxy.prefetching;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import myproxy.httpio.ChunkedInputStream;
import myproxy.httpio.ChunkedOutputStream;
import myproxy.httpio.Request;
import myproxy.httpio.Response;
import myproxy.httpio.URIParser;

/**
 * @author dh
 * @since 2007-09-30
 * 
 * Keeps data for a prefetched entity before, while and
 * after it being fetched.
 *
 */
public class PrefetchedEntity {
	
	/** the buffer which contains the entity's body  */
	private ByteArrayOutputStream _buffer;
	
	/** the response headers */
	private Response _response;
	
	/** the request headers */
	private Request _request;
	
	private boolean _completed;
	
	/** The original URI */
	private String _uri;
	
	/** Timestamp when this entity was completed */
	private Date _completedAt;
	
	private boolean _alreadyParsed;

	// use this constructor for PrefetchedEntities which are storage containers
	// on the local end
	public PrefetchedEntity() {
		_request = new Request();
		_response = new Response();
		_buffer = new ByteArrayOutputStream();
		_completed = false;
	}
	
	// use this constructor for PrefetchedEntities which are to be prefetched
	// at the remote end
	public PrefetchedEntity(Request request) {
		_request = request;
		_uri = _request.getURI().getSource();
		_response = new Response();
		_buffer = new ByteArrayOutputStream();
		_completed = false;
	}

	public Response getResponse() {
		return _response;
	}

	public void setResponse(Response response) {
		this._response = response;
	}
	
	public void writeStream(InputStream in, long length) throws IOException {

		final int bufsize = 4096;
		byte[] buf = new byte[bufsize];
		int read = 0;
		
		if(length != -1)
		{
			while(length > 0)
			{
				read = in.read(buf, 0, ((int)length>bufsize)?bufsize:(int)length);
				if(read == -1)
					throw new IOException("Unexpected end of stream.");
				String buffer = buf.toString();
				_buffer.write(buf, 0, read);
				length -= read;
			}
		}
		else
		{
			while((read = in.read(buf)) != -1)
				_buffer.write(buf, 0, read);
		}
		_buffer.flush();
	}
	
	public void write(byte [] buf) throws IOException {
		_buffer.write(buf);
	}
	
	public byte[] getBuffer() throws IOException {
		return _buffer.toByteArray();
	}
	
	/**
	 * Return the (potentially unencrypted) body contents
	 * 
	 * @return Array of bytes containing the body 
	 * @throws IOException
	 */
	public byte[] getBufferUncompressed() throws IOException {
		if(_response != null) {
			String contentEncoding = _response.getHeaders().getValue("Content-Encoding");
			if(contentEncoding != null && contentEncoding.equals("gzip")) {
				GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(_buffer.toByteArray()));
				ByteArrayOutputStream baos = new ByteArrayOutputStream(_buffer.size()); // conservative assumption
				byte[] buf = new byte[8192];
				int read = 0;
				while( (read = gzipIn.read(buf)) > 0) {
					baos.write(buf, 0, read);
				}
				gzipIn.close();
				return baos.toByteArray();
			}
		}
		
		return _buffer.toByteArray();
	}

	public Request getRequest() {
		return _request;
	}
	
	public String getURI() {
		return _uri;
	}
	
	public void setURI(String uri) {
		_uri = uri;
	}
	

	public void writeChunks(InputStream in) throws IOException {
		ChunkedInputStream  cin  = new ChunkedInputStream(in);
		
		byte[] buf = new byte[4096];
		int read = 0;
		
		cin.startChunk();
		while(cin.chunkSize() > 0)
		{
			while(cin.chunkLeft() > 0)
			{
				read = cin.read(buf);
				if(read == -1)
					throw new IOException("Unexpected end of stream.");
				_buffer.write(buf, 0, read);
			}
			cin.startChunk();
		}
		cin.close();
		
	}

	public boolean isCompleted() {
		return _completed;
	}

	public void setCompleted(boolean _completed) {
		this._completed = _completed;
		this._completedAt = new Date();
	}
	
	public Date getCompletedAt() {
		return _completedAt;
	}

	public boolean isAlreadyParsed() {
		return _alreadyParsed;
	}

	public void setAlreadyParsed(boolean prefetched) {
		_alreadyParsed = prefetched;
	}
	

}
