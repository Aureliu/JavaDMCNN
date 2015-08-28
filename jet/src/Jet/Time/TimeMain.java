// -*- tab-width: 4 -*-
package Jet.Time;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Calendar;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.joda.time.IllegalFieldValueException;

import AceJet.Ace;
import Jet.Lex.Tokenizer;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

public class TimeMain {
	private static final String DOC_DIR = "docs";
	private static final String RESULT_DIR = "result";
	private static final String RULE_FILE = "data/time_rules.yaml";
	private static final Pattern YMDpattern =
		Pattern.compile("(^|\\D)((199|200|201)\\d{5})($|\\D)");
	public static TimeAnnotator timeAnnotator;

	public static void main(String[] args) throws IOException {
		timeAnnotator = new TimeAnnotator(RULE_FILE);
		File docDir = new File(DOC_DIR);
		File[] docFiles = docDir.listFiles();

		for (int i = 0; i < docFiles.length; i++) {
			processDocument(docFiles[i]);
		}
	}

	private static void processDocument(File file) throws IOException {
		String content = readFileToString(file);
		Document doc = new Document(content);
		doc.annotateWithTag("DATETIME");
		doc.annotateWithTag("TEXT");

		processDocument (doc);

		File outputDir = new File(RESULT_DIR);
		if (!outputDir.exists()) {
			outputDir.mkdirs();
		}
		File outputFile = new File(outputDir, file.getName());
		Writer out = new BufferedWriter(new FileWriter(outputFile));
		out.write(doc.writeSGML("TIMEX2").toString());
		out.close();
	}

	/**
	 *  determines the reference time and adds TIMEX2 annotations to all the
	 *  TEXT fields of document <CODE>doc</CODE>.
	 *  <P>
	 *  The reference time (document creation date) is found from 
	 *  <ul>
	 *  <li> a TIMEX2 expression in a DATETIME field of the document  </li>
	 *  <li> a TIMEX2 expression in a DATE_TIME field of the document </li>
	 *  <li> a 8-digit sequence yyyymmdd within the document id       </li>
	 *  </ul>
	 */

	public static void processDocument (Document doc) {
		//
		//  get reference date
		//
		Vector v = doc.annotationsOfType("DATETIME");
		if (v == null || v.size() == 0)
			v = doc.annotationsOfType("DATE_TIME");
		NumberAnnotator numberAnnotator = new NumberAnnotator();
		DateTime ref = new DateTime();
		boolean foundDate = false;

		if (v != null && v.size() > 0) {
			Annotation a = (Annotation) v.get(0);
			Tokenizer.tokenize(doc, a.span());
			numberAnnotator.annotate(doc, a.span());
			timeAnnotator.annotate(doc, a.span(), ref);

			Vector times = doc.annotationsOfType("TIMEX2", a.span());
			if (times != null && times.size() > 0) {
				Annotation time = (Annotation) times.get(0);
				if (time.get("VAL") != null) {
					ref = new DateTime(time.get("VAL"));
					foundDate = true;
				} else {
					System.out.println ("*** Cannot analyze DATETIME time expression " + doc.text(time));
				}
			} else {
				System.out.println ("*** Cannot analyze DATETIME " + doc.text(a));
			}
		}
		
		if (!foundDate) {
			String docId = Ace.getDocId(doc);
			if (docId != null) {
				Matcher m = YMDpattern.matcher(docId);
				if (m.find()) {
				  	DateTime dt = parseDocIdDate(m.group(2));
					if (dt != null) {
						ref = dt;
				  		foundDate = true;
					}
				}
			}
		}

		if (!foundDate)
			System.out.println ("*** Using today's date as reference date.");
		//
		//  tag all TEXT fields (or entire document if no TEXT fields)
		//
		Vector<Annotation> texts = doc.annotationsOfType("TEXT");
		if (texts == null) {
			Span span = doc.fullSpan();
			if (doc.annotationsOfType("token") == null)
				Tokenizer.tokenize(doc, span);
			numberAnnotator.annotate(doc, span);
			timeAnnotator.annotate(doc, span, ref);;
		} else {
			for (Annotation text : texts) {
				Span span = text.span();
				if (doc.annotationsOfType("token", span) == null)
					Tokenizer.tokenize(doc, span);
				numberAnnotator.annotate(doc, span);
				timeAnnotator.annotate(doc, span, ref);
			}
		}
	}

	private static String readFileToString(File file) throws IOException {
		final int BUFFER_SIZE = 1024 * 4;
		char[] buffer = new char[BUFFER_SIZE];
		int readCount;
		Reader reader = new BufferedReader(new FileReader(file));

		StringWriter out = new StringWriter();
		try {
			while ((readCount = reader.read(buffer)) >= 0) {
				out.write(buffer, 0, readCount);
			}
		} finally {
			closeQuitely(reader);
		}

		return out.toString();
	}

	private static void closeQuitely(Reader in) {
		if (in != null) {
			try {
				in.close();
			} catch (IOException ex) {
			}
		}
	}

	private static DateTime parseDocIdDate(String str) {
		int year = Integer.parseInt(str.substring(0, 4));
		int month = Integer.parseInt(str.substring(4, 6));
		int day = Integer.parseInt(str.substring(6, 8));
		try {
			return new DateTime(year, month, day, 0, 0, 0, 0);
		} catch (IllegalFieldValueException e) {
			return null;
		}
	}
}
