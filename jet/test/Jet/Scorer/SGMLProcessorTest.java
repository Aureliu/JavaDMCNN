package Jet.Scorer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.*;
import Jet.Tipster.*;

import junit.framework.JUnit4TestAdapter;
import org.junit.Test;

public class SGMLProcessorTest {
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(SGMLProcessorTest.class);
	}

	@Test
	public void testAnnotations() {
		SGMLProcessor.allTags = true;
		Document doc;
		//
		// read tag with no features
		//
		doc = SGMLProcessor.sgmlToDoc ("I <token>love</token> cats.", "xxx");
		List<Annotation> tokens = doc.annotationsOfType("token");
		assertNotNull (tokens);
		assertEquals (1, tokens.size());
		Annotation token = tokens.get(0);
		assertEquals (2, token.start());
		assertEquals (6, token.end());
		//
		// read tag with feature
		//
		doc = SGMLProcessor.sgmlToDoc ("I <token a=\"b\">love</token> cats.", "xxx");
		tokens = doc.annotationsOfType("token");
		assertNotNull (tokens);
		assertEquals (1, tokens.size());
		token = tokens.get(0);
		assertEquals (2, token.start());
		assertEquals (6, token.end());
		assertEquals ("b", token.get("a"));
		//
		// read empty element tag with no features
		//
		doc = SGMLProcessor.sgmlToDoc ("I <quote/>love cats.", "xxx");
		List<Annotation> quotes = doc.annotationsOfType("quote");
		assertNotNull (quotes);
		assertEquals (1, quotes.size());
		Annotation quote = quotes.get(0);
		assertEquals (2, quote.start());
		assertEquals (2, quote.end());
		//
		// read empty element tag with feature
		//
		doc = SGMLProcessor.sgmlToDoc ("I <quote a=\"b\"/>love cats.", "xxx");
		quotes = doc.annotationsOfType("quote");
		assertNotNull (quotes);
		assertEquals (1, quotes.size());
		quote = quotes.get(0);
		assertEquals (2, quote.start());
		assertEquals (2, quote.end());
		assertEquals ("b", quote.get("a"));
		//
		// read document with XML declaration
		//
		doc = SGMLProcessor.sgmlToDoc ("<?xml version=\"1.0\"?>\n" + 
			"I <token>love</token> cats.", "xxx");
		tokens = doc.annotationsOfType("token");
		assertNotNull (tokens);
		assertEquals (1, tokens.size());
		token = tokens.get(0);
		assertEquals (3, token.start());
		assertEquals (7, token.end());
		//
		// read invalid tags
		//
		doc = SGMLProcessor.sgmlToDoc ("I <>love < >cats.", "xxx");
		String s = doc.text();
		assertEquals ("I <>love < >cats.", s);	
	}
}
