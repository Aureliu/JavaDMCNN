package Jet.Tipster;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.JUnit4TestAdapter;

import org.junit.Test;

import Jet.Lex.Tokenizer;
import Jet.Scorer.SGMLProcessor;

public class DocumentTest {
	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(DocumentTest.class);
	}

	@Test
	public void testAnnotations() {
		String source = "This is a pen.";
		Document doc = new Document(source);
		doc.annotate("token", new Span(10, 13), null);
		assertEquals(doc.writeSGML(null).toString(), "This is a <token>pen</token>.");

		doc.annotate("ENAMEX", new Span(10, 13), null);

		List<Annotation> tokens = doc.annotationsOfType("token");
		assertNotNull(tokens);
		assertEquals(1, tokens.size());

		List<Annotation> names = doc.annotationsOfType("ENAMEX");
		assertNotNull(names);
		assertEquals(1, names.size());

		names = doc.annotationsOfType("ENAMEX", tokens.get(0).span());
		assertNotNull(names);
		assertEquals(1, names.size());

		tokens = doc.annotationsOfType("token", names.get(0).span());
		assertNotNull(tokens);
		assertEquals(1, tokens.size());

		List<Annotation> anns = doc.annotationsAt(10);
		assertNotNull(anns);
		assertEquals(2, anns.size());

		names = doc.annotationsAt(10, "ENAMEX");
		assertNotNull(names);
		assertEquals(1, names.size());

		tokens = doc.annotationsAt(10, "token");
		assertNotNull(tokens);
		assertEquals(1, tokens.size());

		String[] types = {"ENAMEX", "token"};
		anns = doc.annotationsAt(10, types);
		assertNotNull(anns);
		assertEquals(2, anns.size());
	}

	@Test
	public void testWriteSGML() {
		SGMLProcessor.allTags = true;
		String source = "<SENTENCE><token>Test </token><token>of </token><token>writeSGML</token><token>.</token></SENTENCE>";
		Document doc = SGMLProcessor.sgmlToDoc(source, new String[0]);
		doc.setSGMLwrapMargin(0);
		assertEquals(source, doc.writeSGML(null).toString());

		List<Annotation> tokens = doc.annotationsOfType("token");
		assertEquals("<token>Test </token>", doc.writeSGML(null, tokens.get(0).span()).toString());
	}

	@Test
	public void testAnnotationsWithTokens1() throws Exception {
		String sgml = "<ENAMEX TYPE=\"PERSON\">Satoshi Sekine</ENAMEX> wrote <ENAMEX TYPE=\"\">OAK system</ENAMEX>.";
		List<String> expected = Arrays.asList("Satoshi Sekine", "wrote", "OAK system", ".");
		Document doc = SGMLProcessor.sgmlToDoc(sgml, "ENAMEX");
		Tokenizer.tokenize(doc, doc.fullSpan());

		List<Annotation> annotations = annotationsWithTokens(doc, "ENAMEX", doc.fullSpan());
		List<String> actual = annotationListToStringList(doc, annotations);

		assertEquals(expected, actual);
	}

	@Test
	public void testAnnotationsWithTokens2() throws Exception {
		String sgml = "NLP toolkit written by <ENAMEX TYPE=\"\">Ralph Grishman</ENAMEX> is JET.";
		List<String> expected = Arrays.asList("NLP", "toolkit", "written", "by", "Ralph Grishman",
				"is", "JET", ".");
		Document doc = SGMLProcessor.sgmlToDoc(sgml, "ENAMEX");
		Tokenizer.tokenize(doc, doc.fullSpan());

		List<Annotation> annotations = annotationsWithTokens(doc, "ENAMEX", doc.fullSpan());
		List<String> actual = annotationListToStringList(doc, annotations);

		assertEquals(expected, actual);
	}

	@Test
	public void testAnnotationsWithTokens3() throws Exception {
		String sgml = "This is a pen";
		List<String> expected = Arrays.asList("This", "is", "a", "pen");
		Document doc = SGMLProcessor.sgmlToDoc(sgml, "ENAMEX");
		Tokenizer.tokenize(doc, doc.fullSpan());

		List<Annotation> annotations = annotationsWithTokens(doc, "ENAMEX", doc.fullSpan());
		List<String> actual = annotationListToStringList(doc, annotations);

		assertEquals(expected, actual);
	}

        /**
         * Returns list of annotations of type <code>type</code> with tokens which
         * are outside these annotations.
         */

        private List<Annotation> annotationsWithTokens(Document doc, String type, Span span) {
                List<Annotation> annotations = doc.annotationsOfType(type, span);

                if (annotations == null || annotations.size() == 0) {
                        return doc.annotationsOfType("token", span);
                }

                List<Annotation> result = new ArrayList<Annotation>();
                int start = span.start();
                for (Annotation ann : annotations) {
                        if (ann.start() > start) {
                                Span tmpSpan = new Span(start, ann.start());
                                List<Annotation> tmp = doc.annotationsOfType("token", tmpSpan);
                                if (tmp != null) {
                                        result.addAll(tmp);
                                }
                        }
                        result.add(ann);
                        start = ann.end();                }

                if (start < span.end()) {
                        Span tmpSpan = new Span(start, span.end());
                        List<Annotation> tmp = doc.annotationsOfType("token", tmpSpan);
                        if (tmp != null) {
                                result.addAll(tmp);
                        }
                }

                return result;
        }

	private static List<String> annotationListToStringList(Document doc,
			List<Annotation> annotations) {
		List<String> list = new ArrayList<String>();
		for (Annotation ann : annotations) {
			list.add(doc.normalizedText(ann));
		}
		return list;
	}
}
