// -*- tab-width: 4 -*-
package Jet.Tipster;

import Jet.JetTest;
import Jet.Scorer.*;
import Jet.Lisp.*;
import java.util.*;
import java.io.*;

/**
 *  a Document associated with a file.  The document may either by closed (information
 *  resides only on the file) or open (information resides on file and in object in
 *  memory).
 */

public class ExternalDocument extends Document {

	private boolean open;
	private String format;
	private String directory;
	private String fileName;
	// for files in SGML format, a list of the tags which are to be
	// converted to annotations.
	private String[] SGMLtags = new String[0];
	// if true, all tags should be converted to annotations
	private boolean allTags = false;
	//
	private String[] emptyTags = null;

	/**
	 *  creates a new external document associated with file 'fileName'.  The format
	 *  of the file is given by 'format'.  At present on the format 'sgml' (a file
	 *  with SGML markup which is to be converted to annotations) is recognized.
	 */

	public ExternalDocument (String format, String fileName) {
		this.format = format;
		this.directory = null;
		this.fileName = fileName;
		this.open = false;
	}

	public ExternalDocument (String format, String directory, String fileName) {
		this.format = format;
		this.directory = directory;
		this.fileName = fileName;
		this.open = false;
	}

	/**
	 *  sets the list of SGML tags which are to be converted to annotations when
	 *  this document is opened.  Applicable only to documents of format 'sgml'.
	 */

	public void setSGMLtags (String[] tags) {
		SGMLtags = tags;
	}

	/**
	 * if allTags == true, specifies that all SGML tags are to be converted to
	 * annotations when this document is opened.  Applicable only to documents
	 * of format 'sgml'.
	 */

	public void setAllTags (boolean allTags) {
		this.allTags = allTags;
	}

	/**
	 *  specify a list of empty tags -- tags which do not have any corresponding
	 *  close tags and so should be converted to empty Annotations.
	 */

	public void setEmptyTags (String[] tags) {
		emptyTags = tags;
	}

	/**
	 *  opens the externalDocument:  reads the contents of the file, filling the
	 *  text and annotations of the document.  Returns true if the Document
	 *  was successfully opened, or was already open;  returns false if there
	 *  was an error in opening the file.
	 */

	public boolean open() {
		if (!open) {
			try {
				if (format.equals("sgml")) {
					File file = new File(fullFileName());	//return the full file name,including directory and filename
					String line;
					BufferedReader reader = new BufferedReader (
						// (new FileReader(file));
						new InputStreamReader (new FileInputStream(file), JetTest.encoding));
					StringBuffer fileText = new StringBuffer();		//store all text in filename
					while((line = reader.readLine()) != null)
						fileText.append(line + "\n");
					String text = fileText.toString();  //Store the converted text
					SGMLProcessor.allTags = allTags;	
					SGMLProcessor.emptyTags = emptyTags;
					Document doc = SGMLProcessor.sgmlToDoc (this, text, SGMLtags);	//Because format.equals("sgml")
					open = true;
					return true;
				} else if (format.equals("pos")) {
					posRead();
					open = true;
					return true;
				} else {
					System.out.println ("Error opening document " + fileName);
					System.out.println ("Unknown document format.");
					return false;
				}
			} catch (IOException e) {
					System.out.println ("Error opening document " + fileName);
					System.out.println (e);
					return false;
			}
		} else {
			// return true if already open
			return true;
		}
	}

	/**
	 *  reads a file with part-of-speech markup, in the format
	 *  token/pos  token/pos ...
	 *  and converts it to a document with annotation 'constit' with feature 'cat' whose
	 *  value is the part of speech.
	 */

	private void posRead() throws IOException {
		File file = new File(fullFileName());
		String line;
		BufferedReader reader = new BufferedReader(new FileReader(file));
		StringBuffer fileText = new StringBuffer();
		while((line = reader.readLine()) != null) {
			StringTokenizer st = new StringTokenizer(line);
			while (st.hasMoreTokens()) {
         		String tokenAndPos = st.nextToken();
         		int slashIndex = tokenAndPos.lastIndexOf('/');
         		if (slashIndex <= 0) throw new IOException("invalid data");
         		String token = tokenAndPos.substring(0,slashIndex);
         		String pos = tokenAndPos.substring(1+slashIndex).intern();
         		int start = this.length();
         		this.append(token);
         		if (st.hasMoreTokens())
         			this.append(" ");
         		else
         			this.append(" \n");
         		int end = this.length();
         		Span span = new Span(start,end);
         		this.annotate("token", span, new FeatureSet());
         		this.annotate("constit", span, new FeatureSet("cat", pos));
         	}
		}
	}

	/**
	 *  saves the Document to the directory/fileName used for opening the Document.
	 *  The document must be open.
	 */

	public void save () {
		if (!open) {
			System.out.println ("ExternalDocument.save:  attempt to save unopened document " + fileName);
			return;
		}
		try {
			if (format.equals("sgml")) {
				String tagToWrite;
				if (allTags)
					tagToWrite = null;
				else if (SGMLtags.length == 0)
					tagToWrite = "***";  // unused annotation type
				else if (SGMLtags.length == 1)
					tagToWrite = SGMLtags[0];
				else {
					System.out.println ("ExternalDocument.save:  cannot write more than 1 annotation type");
					return;
				}
				String string = writeSGML(tagToWrite).toString();
				File file = new File(fullFileName());
				BufferedWriter writer = new BufferedWriter (
						new OutputStreamWriter (new FileOutputStream(file), JetTest.encoding));
				writeWithSystemNewlines (writer, string);
				writer.close();
			} else {
					System.out.println ("Error saving document " + fileName);
					System.out.println ("Unknown document format.");
			}
		} catch (IOException e) {
				System.out.println ("Error opening document " + fileName);
				System.out.println (e);
		}
	}

	/**
	 *  writes <CODE>string</CODE> to <CODE>writer</CODE>, converting newline
	 *  characters to system-specific newlines.
	 */

	public static void writeWithSystemNewlines (BufferedWriter writer, String string)
			throws IOException {
		for (int i=0; i<string.length(); i++) {
			char c = string.charAt(i);
			if (c == '\n')
				writer.newLine();
			else
				writer.write(c);
		}
	}

	/**
	 *  saves the Document to the originally specified fileName in directory
	 *  'directory'.
	 */

	public void saveIn (String directory) {
		this.directory = directory;
		save();
	}

	/**
	 *  saves the Document to file 'fileName' in directory 'directory'.
	 */

	public void saveAs (String directory, String fileName) {
		this.directory = directory;
		this.fileName = fileName;
		save();
	}

	/**
	 *  returns 'true' if the file has been opened.
	 */

	public boolean isOpen () {
		return open;
	}

	/**
	 *  returns the format of the file holding this document:  'sgml' or 'pos'.
	 */

	public String format () {
		return format;
	}

	/**
	 *  returns the directory associated with the document, or 'null'
	 *  if there is no directory (if the fileName contains the full path).
	 */

	public String directory () {
		return directory;
	}

	/**
	 *  returns the file name associated with the document.
	 */

	public String fileName () {
		return fileName;
	}

	/**
	 *  the full file name, including both the directory and the file name
	 *  within the directory
	 */

	public String fullFileName () {
		if (directory == null || directory.equals(""))
			return fileName;
		else
			return directory + File.separator + fileName;	//add a '\' in the path
	}

}
