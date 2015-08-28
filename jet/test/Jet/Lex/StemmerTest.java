package Jet.Lex;

import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import junit.framework.JUnit4TestAdapter;

import org.junit.BeforeClass;
import org.junit.Test;

import Jet.Format.PTBReader;
import Jet.Format.Treebank;
import Jet.HMM.HMMTagger;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

public class StemmerTest {
	private static HMMTagger tagger;
	private static Stemmer stemmer;
	
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(StemmerTest.class);
	}
	
	@BeforeClass
	public static void init() throws Exception {
		tagger = new HMMTagger();
		tagger.load("data/pos_hmm.txt");
		stemmer = Stemmer.getDefaultStemmer();
	}

	@Test
	public void testStem1() throws Exception {
		Document doc = prepare("The company president has a private plane.");
		stemmer.tagStem(doc, doc.fullSpan());
		Vector<Annotation> tokens = doc.annotationsOfType("token");
		List<String> expected = Arrays.asList("the", null, null, "have", null, null, null, null);
		List<String> actual = getFeatureList(tokens, "stem");
		assertEquals(expected, actual);
	}

	@Test
	public void testStem2() throws Exception {
		Document doc = prepare("This sentence is going to be stemmed.");
		stemmer.tagStem(doc, doc.fullSpan());
		Vector<Annotation> tokens = doc.annotationsOfType("token");
		List<String> expected = Arrays.asList("this", null, "be", "go", null, null, "stem", null);
		List<String> actual = getFeatureList(tokens, "stem");
		assertEquals(expected, actual);
	}

	@Test
	public void testStem3() throws Exception {
		Document doc = prepare("There are swapped.");
		stemmer.tagStem(doc, doc.fullSpan());
		Vector<Annotation> tokens = doc.annotationsOfType("token");
		List<String> expected = Arrays.asList("there", "be", "swap", null);
		List<String> actual = getFeatureList(tokens, "stem");
		assertEquals(expected, actual);
	}
	
	@Test
	public void testNN() throws Exception {
		Document doc = prepare("He has BMW.");
		stemmer.tagStem(doc, doc.fullSpan());
		Vector<Annotation> tokens = doc.annotationsOfType("token");
		List<String> expected = Arrays.asList("he", "have", null, null);
		List<String> actual = getFeatureList(tokens, "stem");
		assertEquals(expected, actual);
	}
	
	@Test
	public void testParseTree() throws Exception {
		Document doc = prepareFromPTB("( (S (NP (PRN He)) (VP (VBZ is) (NP (NNP Mohamad)))))");
		stemmer.tagStem(doc, doc.fullSpan());
		Vector<Annotation> tokens = doc.annotationsOfType("token");
		assertEquals(3, tokens.size());
		List<String> expected = Arrays.asList("he", "be", null);
		List<String> actual = getFeatureList(tokens, "stem");
		assertEquals(expected, actual);
	}

	private Document prepare(String text) throws Exception {
		Document doc = new Document(text);
		Tokenizer.tokenize(doc, doc.fullSpan());
		tagger.tagPenn(doc, doc.fullSpan());
		return doc;
	}
	
	private Document prepareFromPTB(String ptb) throws Exception {
		PTBReader reader = new PTBReader();
		reader.setAddingToken(true);
		Treebank treebank = reader.load(new StringReader(ptb));
		return treebank.getDocument();
	}
	
	private <T> List<T> getFeatureList(Vector<Annotation> anns, String name) {
		List<T> result = new ArrayList<T>();
		for (Annotation ann : anns) {
			result.add((T) ann.get(name));
		}
		return result;
	}
}
