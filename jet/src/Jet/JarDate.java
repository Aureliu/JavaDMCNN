package Jet;

import java.util.Date;
import java.text.DateFormat;
import java.net.URL;
import java.net.URLConnection;
import java.net.JarURLConnection;
import java.io.PrintStream;
import java.io.IOException;

/**
 *  provides a method for determining and printing the date on which a jar
 *  was created.
 */
 
class JarDate {

	public static void main (String[] args) {
		print(System.out);
	}
	
	/**
	 *  if this class was loaded from a jar, print the date on which the jar
	 *  was created (otherwise nothing is printed) to PrintStream <CODE>ps</CODE>.
	 */
	
	public static void print (PrintStream ps) {		
		try {
			URL url = JarDate.class.getResource("JarDate.class");
			URLConnection urlConn = url.openConnection();
			if (urlConn instanceof JarURLConnection) {
				Date date = new Date(urlConn.getLastModified());
				ps.println ("Jet jar created " + DateFormat.getDateInstance().format(date));
			}
		} catch (IOException e) {
		}
	}
}			