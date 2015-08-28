// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.62
//Copyright(c): 2011
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet.Chunk;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import junit.framework.JUnit4TestAdapter;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.*;

import Jet.Tipster.*;
import Jet.Lex.Tokenizer;
import Jet.Scorer.NameTagger;
import Jet.Scorer.SGMLProcessor;

/**
 *  tests for NameConstraints class
 */

public class NameConstraintsTest {

	private static NameTagger nameTagger;
	static final String meFileName = "acedata/Ace05meneModel";

	@BeforeClass
	public static void init() throws Exception {
		nameTagger = new MENameTagger();
                nameTagger.load(meFileName);
	}

	@Test // name known to tagger
	public void testConstraints1 () throws Exception {
		match ( "His name is Bill Clinton. ",
			"His name is <ENAMEX TYPE=PERSON>Bill Clinton</ENAMEX>." );
	}

	@Test // name entirely unknown to tagger
	public void testConstraints2 () throws Exception {
		match ( "Her name is Heng Ji. ",
			"Her name is Heng Ji." );
	}

	@Test // simple test of <isName> surrounding entire name
	public void testConstraints3 () throws Exception {
		match ( "Her name is <isName type=person>Heng Ji</isName>. ",
			"Her name is <ENAMEX TYPE=PERSON>Heng Ji</ENAMEX>." );
	}

	@Test // simple test of <isExactName> surrounding entire name
	public void testConstraints4 () throws Exception {
		match ( "Her name is <isExactName type=person>Heng Ji</isExactName>. ",
			"Her name is <ENAMEX TYPE=PERSON>Heng Ji</ENAMEX>." );
	}

	@Test // test of <isName> on last name with well known first name
	public void testConstraints5 () throws Exception {
		match ( "Her name is Mary <isName type=person>Liao</isName>. ",
			"Her name is <ENAMEX TYPE=PERSON>Mary Liao</ENAMEX>." );
	}

	@Test // comparable test of <isExactName> (incorrect use of isExactName)
	public void testConstraints6 () throws Exception {
		match ( "Her name is Mary <isExactName type=person>Liao</isExactName>. ",
			"Her name is Mary <ENAMEX TYPE=PERSON>Liao</ENAMEX>." );
	}

	@Test // test of <isName> surrounding individual tokens of name
	public void testConstraints7 () throws Exception {
		match ( "Her name is <isName type=person>Heng</isName> <isName type=person>Ji</isName>. ",
			"Her name is <ENAMEX TYPE=PERSON>Heng Ji</ENAMEX>." );
	}

	@Test // test of <isExactName> surrounding individual tokens of name (incorrect use of isExactName)
	public void testConstraints8 () throws Exception {
		match ( "Her name is <isExactName type=person>Heng</isExactName> <isExactName type=person>Ji</isExactName>. ",
			"Her name is <ENAMEX TYPE=PERSON>Heng</ENAMEX> <ENAMEX TYPE=PERSON>Ji</ENAMEX>." );
	}

	@Test // test of <isName> with no type specification
	public void testConstraints9 () throws Exception {
		match ( "He works for the company. ",
			"He works for the company. " );
		match ( "He works for the <isName>company</isName>. ",
			"He works for the <ENAMEX TYPE=ORGANIZATION>company</ENAMEX>." );
		match ( "He works for hosni mubarak today. ",
			"He works for hosni mubarak today. " );
		match ( "He works for <isName>hosni mubarak</isName> today. ",
			"He works for <ENAMEX TYPE=PERSON>hosni mubarak</ENAMEX> today. " );
		match ( "He works for New York University. ",
			"He works for <ENAMEX TYPE=ORGANIZATION>New York University</ENAMEX>. " );
		match ( "He works for <isName>New York University</isName>. ",
			"He works for <ENAMEX TYPE=ORGANIZATION>New York University</ENAMEX>. " );
	}

	@Test // name known to tagger but blocked by isName/isExactName type=other
	public void testConstraints10 () throws Exception {
		match ( "His name is <isName type=other>Bill Clinton</isName>. ",
			"His name is Bill Clinton." );
		match ( "His name is <isExactName type=other>Bill Clinton</isExactName>. ",
			"His name is Bill Clinton." );
		match ( "His name <isName type=other>is</isName> Bill Clinton. ",
			"His name is <ENAMEX TYPE=PERSON>Bill Clinton</ENAMEX>." );
	}

	static void match (String neInput, String neOutput) {
		SGMLProcessor.allTags = true;
		Document doc1 = SGMLProcessor.sgmlToDoc(neInput, "");
		doc1.stretchAll();
		Span span1 = doc1.fullSpan();
		Tokenizer.tokenize(doc1, span1);
		nameTagger.tag(doc1, span1);
		doc1.shrink("ENAMEX");
		Vector<Annotation> names1 = doc1.annotationsOfType("ENAMEX");
		
		Document doc2 = SGMLProcessor.sgmlToDoc(neOutput, "");
		Vector<Annotation> names2 = doc2.annotationsOfType("ENAMEX");

		if (names1 == null && names2 == null) return;
		assertTrue ("names1==null", names1 != null);
		assertTrue ("names2==null", names2 != null);
		assertEquals ("number of ENAMEX", names2.size(), names1.size());
		for (int i = 0; i < names1.size(); i++) {
			Annotation a1 = names1.get(i);
			Annotation a2 = names2.get(i);
			assertEquals ("spans unequal", a2.span(), a1.span());
			assertEquals ("types unequal", a2.get("TYPE"), a1.get("TYPE"));
		}
		return;
	}
		
}
