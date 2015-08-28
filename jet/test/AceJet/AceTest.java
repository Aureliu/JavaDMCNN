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
 *  minimal test for Ace class
 */

public class AceTest {

	static final String propertyFile = "props/kdd11.properties";

	@BeforeClass
	public static void init() throws Exception {
                Ace.init (propertyFile);
		SGMLProcessor.allTags = true;
	}

	// document with an event
	@Test
	public void testAce1 () throws Exception {
		String text = "<TEXT>Mr. Smith died. </TEXT>";
		Document doc = SGMLProcessor.sgmlToDoc(text, "");
		AceDocument aceDoc = Ace.processDocument (doc, "TD1", "TD1", null);

		assertTrue ("aceDoc == null", aceDoc != null);
		ArrayList<AceEntity> entities = aceDoc.entities;
		assertEquals("number of entities", 1, entities.size());
		AceEntity entity = entities.get(0);
		assertEquals ("ID of entity", "TD1-1", entity.id);
		ArrayList<AceRelation> relations = aceDoc.relations;
		assertEquals("number of relations", 0, relations.size());
		ArrayList<AceEvent> events = aceDoc.events;
		assertEquals("number of events", 1, events.size());

		/* -- code to print Ace document --
		StringWriter writer = new StringWriter();
		PrintWriter apf = new PrintWriter(writer);
                aceDoc.write(apf, doc);
		String output = writer.toString();
		System.out.println (output);
		*/
	}

	// document with a relation
	@Test
	public void testAce2 () throws Exception {
		String text = "<TEXT>French President Sarkozy took a nap. </TEXT>";
		Document doc = SGMLProcessor.sgmlToDoc(text, "");
		AceDocument aceDoc = Ace.processDocument (doc, "TD2", "TD2", null);

		assertTrue ("aceDoc == null", aceDoc != null);
		ArrayList<AceEntity> entities = aceDoc.entities;
		assertEquals("number of entities", 2, entities.size());
		AceEntity entity = entities.get(0);
		assertEquals ("ID of entity", "TD2-1", entity.id);
		ArrayList<AceRelation> relations = aceDoc.relations;
		assertEquals("number of relations", 1, relations.size());
		ArrayList<AceEvent> events = aceDoc.events;
		assertEquals("number of events", 0, events.size());
	}
}
