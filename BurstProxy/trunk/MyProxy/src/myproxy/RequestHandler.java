package myproxy;

import java.io.IOException;

import myproxy.httpio.MessageFormatException;

public interface RequestHandler {

	public void handleRequest() throws IOException, HTTPException, MessageFormatException;
	public void reuseHandler(MyProxy controller, Handler handler);
	
}
