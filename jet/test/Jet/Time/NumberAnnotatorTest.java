package Jet.Time;

import java.util.Vector;

import junit.framework.JUnit4TestAdapter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import Jet.Lex.Tokenizer;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;

public class NumberAnnotatorTest {
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(NumberAnnotatorTest.class);
	}

	private NumberAnnotator numberAnnotator = new NumberAnnotator();

	@Before
	public void setUp() {
	}

	@After
	public void tearDown() {
	}

	@Test
	public void testSimple() throws Exception {
		assertSimple("one", 1);
		assertSimple("two", 2);
		assertSimple("three", 3);
		assertSimple("four", 4);
		assertSimple("five", 5);
		assertSimple("six", 6);
		assertSimple("seven", 7);
		assertSimple("eight", 8);
		assertSimple("nine", 9);
		assertSimple("ten", 10);
		assertSimple("Eleven", 11);
		assertSimple("twelve", 12);
		assertSimple("thirteen", 13);
		assertSimple("fourteen", 14);
		assertSimple("fifteen", 15);
		assertSimple("sixteen", 16);
		assertSimple("seventeen", 17);
		assertSimple("eighteen", 18);
		assertSimple("nineteen", 19);

		assertSimple("twenty", 20);
		assertSimple("thirty", 30);
		assertSimple("forty", 40);
		assertSimple("fifty", 50);
		assertSimple("sixty", 60);
		assertSimple("seventy", 70);
		assertSimple("eighty", 80);
		assertSimple("ninety", 90);
		assertSimple("hundred", 100);
	}

	@Test
	public void testManyDigits() {
		assertSimple("2004 05", 2004, 5);
	}

	@Test
	public void testOrdinal() {
		assertSimple("the fourth of November", _(4, true));
		assertSimple("fifth", _(5, true));
		assertSimple("thousandth", _(1000, true));
		assertSimple("fifty second", _(52, true));
	}

	private void assertSimple(String str, Number... expected) {
		Document doc = new Document(str);
		Tokenizer.tokenize(doc, doc.fullSpan());
		numberAnnotator.annotate(doc);

		Vector<Annotation> numbers = doc.annotationsOfType("number");

		System.out.println(doc.writeSGML(null));

		if (expected != null) {
			assertNotNull(numbers);
			assertEquals(expected.length, numbers.size());

			for (int i = 0; i < expected.length; i++) {
				assertEquals(expected[i], numbers.get(i).get("value"));
			}
		} else {
			assertNull(numbers);
		}
	}

	private void assertSimple(String str, AttrPair... expected) {
		Document doc = new Document(str);
		Tokenizer.tokenize(doc, doc.fullSpan());
		numberAnnotator.annotate(doc);

		Vector<Annotation> numbers = doc.annotationsOfType("number");

		System.out.println(doc.writeSGML(null));

		if (expected != null) {
			assertNotNull(numbers);
			assertEquals(expected.length, numbers.size());

			for (int i = 0; i < expected.length; i++) {
				Boolean o = (Boolean) numbers.get(i).get("ordinal");
				boolean ordinal = o != null && o.booleanValue(); 
				assertEquals(expected[i].value, numbers.get(i).get("value"));
				assertEquals(expected[i].ordinal, ordinal);
			}
		} else {
			assertNull(numbers);
		}
	}

	private static class AttrPair {
		Number value;

		boolean ordinal;

		public AttrPair(Number value, boolean ordinal) {
			super();
			this.value = value;
			this.ordinal = ordinal;
		}
	}

	private static AttrPair _(int value, boolean ordinal) {
		return new AttrPair(value, ordinal);
	}
}
