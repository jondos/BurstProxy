package myproxy.prefetching;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import myproxy.httpio.URIParser;

public class PrefetchUtils {
	
	
	public static PrefetchingParser getHTMLParser() {
		return new JerichoHTMLParser();
	}
	

	public static PrefetchingParser getCSSParser() {
		return new CSSParser();
	}

	/**
	 * Returns a list of URLs contained in the HTML response body. This method
	 * create absolute URLs by prepending relative URIs with the specified baseUri.
	 * 
	 * @param baseUri the base URI of the document
	 * @param body the contents of the document
	 * @throws MalformedURLException 
	 */
	/*public static List findURLsInHTML(URIParser baseUri, String body) throws MalformedURLException{
		
		PrefetchingParser parser = new JerichoHTMLParser(baseUri, body);
		return parser.findURLsInResponse();
	}*/

	/**
	 * Returns a list of URLs contained in the CSS response body. This method
	 * create absolute URLs by prepending relative URIs with the specified baseUri.
	 * 
	 * @param baseUri the base URI of the document
	 * @param body the contents of the document
	 * @throws MalformedURLException 
	 */
	
	/*public static List findURLsInCSS(URIParser baseUri, String body) throws MalformedURLException{
		
		PrefetchingParser parser = new CSSParser(baseUri, body);
		return parser.findURLsInResponse();
	}*/


	
	/**
	 * For testing purposes you can execute this class from the shell.
	 * It will read an HTML file from STDIN and output any prefetchable
	 * entities found.
	 * 
	 * The first command line parameter must be the URI of the page
	 * (e.g. http://www.google.de/ if you supply the contents of this page)
	 * 
	 * @param args Commandline arguments
	 */
	public static void main(String args[]) throws Exception {
		if(args.length == 0) {
			System.err.println("You have to specify the URL of the page as 1st cmdline argument.");
			return;
		}
		
		String baseURI = args[0];
		
		URIParser uri = new URIParser();
		uri.parse(baseURI);
		String body = StdIn.readAll(); 
		

		List parsers = new ArrayList();
		
		PrefetchingParser parser;
		parser = new JavaHTMLParser();
		parsers.add(parser);
		parser = new JerichoHTMLParser();
		parsers.add(parser);
		parser = new CSSParser();
		parsers.add(parser);

		for(Iterator i=parsers.iterator();i.hasNext();) {
			parser = (PrefetchingParser) i.next();
			System.out.println("Parser "+parser.getClass().toString());
			
			long start = System.currentTimeMillis();
			List urls = parser.findURLsInResponse(uri,body);
			long end = System.currentTimeMillis();
			
			System.out.println("Found "+urls.size()+" URLs in " + (end-start) + " milliseconds");
			Iterator it = urls.iterator();
			
			while(it.hasNext()) {
				System.out.println(it.next());
			}
		}
	}

}

/**
 * This class is available at
 * http://www.cs.princeton.edu/introcs/15inout/StdIn.java.html
 * http://www.cs.princeton.edu/introcs/stdlib/
 *
 * It simplifies reading from the command line (used in main method of PrefetchUtils)
 */
final class StdIn {

    // assume Unicode UTF-8 encoding
    private static String charsetName = "UTF-8";

    private static Scanner scanner = new Scanner(System.in, charsetName);

    // can't instantiate
    private StdIn() { }

    // return true if only whitespace left
    public static boolean isEmpty()        { return !scanner.hasNext();      }

    // next string, int, double, float, long, byte, boolean
    public static String  readString()     { return scanner.next();          }
    public static int     readInt()        { return scanner.nextInt();       }
    public static double  readDouble()     { return scanner.nextDouble();    }
    public static float   readFloat()      { return scanner.nextFloat();     }
    public static short   readShort()      { return scanner.nextShort();     }
    public static long    readLong()       { return scanner.nextLong();      }
    public static byte    readByte()       { return scanner.nextByte();      }

    // read in a boolean, allowing "true" or "1" for true and "false" or "0" for false
    public static boolean readBoolean() {
        String s = readString();
        if (s.equalsIgnoreCase("true"))  return true;
        if (s.equalsIgnoreCase("false")) return false;
        if (s.equals("1"))               return true;
        if (s.equals("0"))               return false;
        throw new java.util.InputMismatchException();
    }


    // read until end of line
    public static String readLine()        { return scanner.nextLine();      }

    // read next char
    // a complete hack and inefficient - email me if you have a better
    public static char readChar() {
        // (?s) for DOTALL mode so . matches a line termination character
        // 1 says look only one character ahead
        // consider precompiling the pattern
        String s = scanner.findWithinHorizon("(?s).", 1);
        return s.charAt(0);
    }


    // return rest of input as string
    public static String readAll() {
        if (!scanner.hasNextLine()) return null;

        // reference: http://weblogs.java.net/blog/pat/archive/2004/10/stupid_scanner_1.html
        return scanner.useDelimiter("\\A").next();
    }




    // This method is just here to test the class
    public static void main(String[] args) {

        System.out.println("Type a string: ");
        String s = StdIn.readString();
        System.out.println("Your string was: " + s);
        System.out.println();

        System.out.println("Type an int: ");
        int a = StdIn.readInt();
        System.out.println("Your int was: " + a);
        System.out.println();

        System.out.println("Type a boolean: ");
        boolean b = StdIn.readBoolean();
        System.out.println("Your boolean was: " + b);
        System.out.println();
    }

}




