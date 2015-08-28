// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.63
//Copyright(c): 2012
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package AceJet;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import junit.framework.JUnit4TestAdapter;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;
import java.io.*;

import Jet.JetTest;
import Jet.Tipster.*;
import Jet.Scorer.SGMLProcessor;

/**
 *  test for APFtoXML class
 */

public class APFtoXMLTest {

	static final String propertyFile = "props/kdd11.properties";

	@BeforeClass
	public static void init() throws Exception {
                Ace.init (propertyFile);
		SGMLProcessor.allTags = true;
	}

	/**
	 *	test 'sentence' tags
	 */

	@Test
	public void testAPFtoXML1 () throws Exception {
		String text = "<TEXT>Cats eat fish. So do people.</TEXT>";
		Document doc = SGMLProcessor.sgmlToDoc(text, "");
		Document doc2 = new Document(doc);
		AceDocument aceDoc = Ace.processDocument (doc, "TD", "TD", null);
		APFtoXML.clearFlags();
		APFtoXML.setFlag("sentences");
		String xml = APFtoXML.processDocument(doc2, aceDoc);

		// System.out.println(xml);
		assertEquals
			("XML", 
			 "<TEXT><sentence ID=\"SENT-1\">Cats eat fish.</sentence> <sentence ID=\"SENT-2\">So do people.</sentence></TEXT>",
			 xml);
	}

	/**
	 *  test 'timex' tags
	 */

	@Test
	public void testAPFtoXML2 () throws Exception {
		String text = "<TEXT>Mr. Smith died on December 7, 1941. </TEXT>";
		Document doc = SGMLProcessor.sgmlToDoc(text, "");
		Document doc2 = new Document(doc);
		AceDocument aceDoc = Ace.processDocument (doc, "TD", "TD", null);
		APFtoXML.clearFlags();
		APFtoXML.setFlag("timex");
		String xml = APFtoXML.processDocument(doc2, aceDoc);

		// System.out.println(xml);
		assertEquals
			("XML", 
			 "<TEXT>Mr. Smith died on <timex2 val=\"1941-12-07\">December 7, 1941</timex2>. </TEXT>", 
			 xml);
	}

	/**
	 *  test 'names' tags
	 */

	@Test
	public void testAPFtoXML3 () throws Exception {
		String text = "<TEXT><P>Mr. Smith died today.\nSo did Mrs. Smith.</P></TEXT>";
		Document doc = SGMLProcessor.sgmlToDoc(text, "");
		Document doc2 = new Document(doc);
		AceDocument aceDoc = Ace.processDocument (doc, "TD", "TD", null);
		APFtoXML.clearFlags();
		APFtoXML.setFlag("names");
		String xml = APFtoXML.processDocument(doc2, aceDoc);

		// System.out.println(xml);
		assertEquals
			("XML", 
			 "<TEXT><P>Mr. <ENAMEX TYPE=\"PERSON\">Smith</ENAMEX> died today.\nSo did Mrs. <ENAMEX TYPE=\"PERSON\">Smith</ENAMEX>.</P></TEXT>",
			 xml);
	}

	/**
	 *  test 'mentions' tags
	 */

	@Test
	public void testAPFtoXML4 () throws Exception {
		String text = "<TEXT>Fred lives in Boston. </TEXT>";
		Document doc = SGMLProcessor.sgmlToDoc(text, "");
		Document doc2 = new Document(doc);
		AceDocument aceDoc = Ace.processDocument (doc, "TD", "TD", null);
		APFtoXML.clearFlags();
		APFtoXML.setFlag("mentions");
		String xml = APFtoXML.processDocument(doc2, aceDoc);

		// System.out.println(xml);
		assertEquals
			("XML", 
			 "<TEXT><mention entity=\"0\">Fred</mention> lives in <mention entity=\"1\">Boston</mention>. </TEXT>",
			 xml);
	}

}
