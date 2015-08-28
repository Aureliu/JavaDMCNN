// -*- tab-width: 4 -*-
package Jet.Tipster;

import java.util.*;
import java.io.*;

/**
 *  a set of ExternalDocuments.
 *  <p>
 *  The DocumentCollection is represented by a file with one line for each
 *  ExternalDocument in the DocumentCollection.  The line consist of either
 *  <ul><li>
 *  the file name of the Document (if the Document is in SGML representation)
 *  <li>
 *  the type (external representation) of the Document, a vertical bar (|), and
 *  the file name for the Document.
 *  </ul>
 *  Currently types 'sgml' and 'pos' are supported.
 *  If the file name of the Document is a relative path, and the DocumentCollection
 *  is created with no explicit directory specified, the path is interpreted as
 *  relative to the directory containing the DocumentCollection file.
 */

public class DocumentCollection {

	String fileName;
	String directory;
	ArrayList documents;
	boolean open;
	static final String DEFAULT_DOC_TYPE = "sgml";

	/**
	 *  create a new DocumentCollection based on file 'fileName'.
	 */

	public DocumentCollection (String fileName) {
		this.fileName = fileName;
		this.directory = null;
		documents = new ArrayList();
		open = false;
	}
	
	/**
	 *  create a new DocumentCollection based on file <CODE>fileName</CODE>,
	 *  where the paths are relative to <CODE>directory</CODE>.
	 */
	
	public DocumentCollection (String directory, String fileName) {
		this.fileName = fileName;
		this.directory = directory;
		documents = new ArrayList();
		open = false;
	}
	
	/**
	 *  read the information about the Collection from an external file.
	 *  A DocumentCollection must be opened before other operations can be
	 *  performed on the collection.
	 */

	public boolean open () {
		if (open) {
			return true;
		} else {
			try {
				File collectionFile = new File(fileName);
				String collectionDirectory = directory != null ? directory : collectionFile.getParent();
				String line;
				BufferedReader reader = new BufferedReader(new FileReader(collectionFile));
				while((line = reader.readLine()) != null) {
					int pos = line.indexOf('|');
					if (pos != 0 && pos < line.length()-1) {
						String docType, docFileName;
						if (pos > 0) {
							docType = line.substring(0,pos);
							docFileName = line.substring(pos+1);
						} else {
							docType = DEFAULT_DOC_TYPE;
							docFileName = line;
						}
						File docFile = new File (docFileName);
						if (docFile.isAbsolute())
							documents.add(new ExternalDocument(docType, docFileName));
						else
							documents.add(new ExternalDocument(docType, collectionDirectory, docFileName));
					} else {
						System.out.println ("Error opening collection " + fileName);
						System.out.println ("Invalid line: " + line);
						return false;
					}
				}
				open = true;
				return true;
			} catch (IOException e) {
				System.out.println ("Error opening collection " + fileName);
				System.out.println (e);
				return false;
			}
		}
	}

	/**
	 *  save all the Documents in the Collection.  This assumes that the
	 *  Collection and all its Documents are open.
	 */

	public void save () {
		for (int i=0; i<documents.size(); i++) {
			ExternalDocument doc = (ExternalDocument) documents.get(i);
			doc.save();
		}
	}

	/**
	 *  save the Collection to file 'newFileName', and then save all the
	 *  Documents in the Collection.  If the Documents in the Collection are
	 *  specified by relative file names, the Documents will be saved as new
	 *  files whose paths are relative to 'newFileName'.  This operation
	 *  assumes that the Collection and all its Documents are open.
	 */

	public void saveAs (String newFileName) {
		File collectionFile = new File(newFileName);
		String collectionDirectory = directory != null ? directory : collectionFile.getParent();
		saveAs(newFileName, collectionDirectory);
	}

	/**
	 * @param parsedCollection
	 * @param parseDir
	 */
	public void saveAs(String newFileName, String collectionDirectory) {
		try {
			File collectionFile = new File(newFileName);
			String line;
			BufferedWriter writer = new BufferedWriter(new FileWriter(collectionFile));
			for (int i=0; i<documents.size(); i++) {
				ExternalDocument doc = (ExternalDocument) documents.get(i);
				doc.saveIn(collectionDirectory);
				String format = doc.format();
				writer.write(format, 0, format.length());
				writer.write("|", 0, 1);
				String docFileName = doc.fileName();
				writer.write(docFileName, 0, docFileName.length());
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			System.out.println ("Error saving collection " + fileName);
			System.out.println (e);
		}
	}

	/**
	 * @param parsedCollection
	 * @param parseDir
	 */
	public void saveAsAbsolute(String newFileName, String collectionDirectory) {
		try {
			File collectionFile = new File(newFileName);
			String line;
			BufferedWriter writer = new BufferedWriter(new FileWriter(collectionFile));
			for (int i=0; i<documents.size(); i++) {
				ExternalDocument doc = (ExternalDocument) documents.get(i);
				doc.saveIn(collectionDirectory);
				String format = doc.format();
				writer.write(format, 0, format.length());
				writer.write("|", 0, 1);
				String docFileName = doc.fullFileName();
				writer.write(docFileName, 0, docFileName.length());
				writer.newLine();
			}
			writer.close();
		} catch (IOException e) {
			System.out.println ("Error saving collection " + fileName);
			System.out.println (e);
		}
	}

	/**
	 *  returns the number of documents in the Collection.
	 */

	public int size () {
		return documents.size();
	}

	/**
	 *  returns the i-th document in the Collection.
	 */

	public ExternalDocument get (int i) {
		return (ExternalDocument) documents.get(i);
	}

	/**
	 *  returns the fileName (not the full path) of the Collection.
	 */

	public String getName () {
		return (new File(fileName)).getName();
	}

}
