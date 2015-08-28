// -*- tab-width: 4 -*-
package Jet.Util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * Utility class for input and output.
 * 
 * @author Akira ODA
 */
public class IOUtils {
	/**
	 * Closes <code>java.io.Closeable</code> object in safe.
	 * 
	 * @param closeable
	 */
	public static void closeQuietly(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException ex) {
			}
		}
	}

	/**
	 * Reads String from file.
	 * 
	 * @param file
	 *            file to be read
	 * @param encoding
	 *            file encoding
	 * @return content which is readed from file
	 * @throws IOException
	 */
	public static String readFile(File file, String encoding) throws IOException {
		InputStream in = null;
		Reader reader = null;

		try {
			in = new FileInputStream(file);
			in = new BufferedInputStream(in);
			reader = new InputStreamReader(in, encoding);

			int ch;
			StringBuilder buffer = new StringBuilder();
			while ((ch = reader.read()) != -1) {
				buffer.append((char) ch);
			}

			return buffer.toString();
		} finally {
			closeQuietly(reader);
			closeQuietly(in);
		}
	}

	/**
	 * Writes content to file.
	 * 
	 * @param file
	 *            the file for writing content
	 * @param encoding
	 *            file encoding
	 * @param content
	 *            the content to be writed to file.
	 * @throws IOException
	 *             if I/O error occurs.
	 */
	public static void writeFile(File file, String encoding, CharSequence content)
			throws IOException {
		OutputStream out = null;
		Writer writer = null;

		try {
			out = new FileOutputStream(file);
			out = new BufferedOutputStream(out);
			writer = new OutputStreamWriter(out, encoding);
			writer.append(content);
		} finally {
			closeQuietly(writer);
			closeQuietly(out);
		}
	}

	/**
	 * Returns BufferedWriter for writing to <code>file</code>.
	 */
	public static BufferedWriter getBufferedWriter(File file, Charset charset)
			throws FileNotFoundException {
		return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), charset));
	}

	/**
	 * Returns BufferedWriter for writing to <code>file</code>.
	 */
	public static BufferedWriter getBufferedWriter(File file, String encoding)
			throws FileNotFoundException {
		Charset charset = Charset.forName(encoding);
		return getBufferedWriter(file, charset);
	}

	/**
	 * Returns BufferedReader for reading from <code>file</code>.
	 */
	public static BufferedReader getBufferedReader(File file, Charset charset)
			throws FileNotFoundException {
		return new BufferedReader(new InputStreamReader(new FileInputStream(file), charset));
	}

	/**
	 * Returns BufferedReader for reading from <code>file</code>.
	 */
	public static BufferedReader getBufferedReader(File file, String encoding)
			throws FileNotFoundException {
		Charset charset = Charset.forName(encoding);
		return getBufferedReader(file, charset);
	}
}
