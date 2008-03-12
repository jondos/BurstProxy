package myproxy;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

//This custom formatter formats parts of a log record to a single line
public class SingleLineFormatter extends Formatter {
	// This method is called for every log records
	public String format(LogRecord rec) {
		StringBuffer buf = new StringBuffer(1000);
		buf.append(rec.getLevel());
		buf.append(' ');
		buf.append(rec.getMillis());
		buf.append(' ');
		buf.append(formatMessage(rec));
		buf.append('\n');
		return buf.toString();
	}

	// This method is called just after the handler using this
	// formatter is created
	//public String getHead(Handler h) {}

	// This method is called just after the handler using this
	// formatter is closed
	//public String getTail(Handler h) { }
}
