package Jet.Time;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.FileInputStream;
import java.io.StringReader;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import junit.framework.JUnit4TestAdapter;

import org.ho.yaml.Yaml;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;

import Jet.Lex.Tokenizer;
import Jet.Lisp.FeatureSet;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.Span;

public class TimeAnnotatorTest {
	private NumberAnnotator numberAnnotator;

	private TimeAnnotator timeAnnotator;

	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(TimeAnnotatorTest.class);
	}

	@Before
	public void setUp() throws Exception {
		numberAnnotator = new NumberAnnotator();
		timeAnnotator = new TimeAnnotator();
		timeAnnotator.load(new FileInputStream("data/time_rules.yaml"));
	}

	@Test
	public void testSimple() throws Exception {
		DateTime ref = new DateTime(2000, 4, 5, 0, 0, 0, 0);
		assertSimpleTime("today", "2000-04-05", ref);
		assertSimpleTime("yesterday", "2000-04-04", ref);
		assertSimpleTime("tomorrow", "2000-04-06", ref);

		assertSimpleTime("next month", "2000-05", ref);
		assertSimpleTime("last month", "2000-03", ref);
		assertSimpleTime("a month later", "2000-05", ref);
		assertSimpleTime("a month ago", "2000-03", ref);

		assertSimpleTime("next year", "2001", ref);
		assertSimpleTime("past year", "1999", ref);
		assertSimpleTime("last year", "1999", ref);
		assertSimpleTime("a year later", "2001-04-05", ref);
		assertSimpleTime("a year ago", "1999-04-05", ref);
		assertSimpleTime("a year earlier", "1999-04-05", ref);
		assertSimpleTime("one year ago", "1999-04-05", ref);
		assertSimpleTime("one year earlier", "1999-04-05", ref);
		assertSimpleTime("year earlier", "1999-04-05", ref);

		assertSimpleTime("3 years ago", "1997", ref);
		assertSimpleTime("four years later", "2004", ref);

		assertSimpleTime("4 months ago", "1999-12", ref);
		assertSimpleTime("8 months later", "2000-12", ref);

		assertSimpleTime("2005", "2005", ref);
		assertSimpleTime("May 2001", "2001-05", ref);
		assertSimpleTime("May 15, 2001", "2001-05-15", ref);
		assertSimpleTime("9/24/1982", "1982-09-24", ref);
	}

	@Test
	public void testSimpleWeek() throws Exception {
		DateTime ref = new DateTime(2005, 1, 1, 0, 0, 0, 0);
		assertSimpleTime("this week", "2004-W53", ref);
		assertSimpleTime("last week", "2004-W52", ref);
		assertSimpleTime("next week", "2005-W01", ref);
	}

	@Test
	public void testSimpleMonth() throws Exception {
		DateTime ref = new DateTime(2005, 1, 1, 0, 0, 0, 0);
		assertSimpleTime("May", "2005-05", ref);
		assertSimpleTime("December", "2004-12", ref);

		ref = new DateTime(2005, 5, 5, 0, 0, 0, 0);
		assertSimpleTime("April", "2005-04", ref);
		assertSimpleTime("November", "2005-11", ref);

		ref = new DateTime(2005, 11, 4, 0, 0, 0, 0);
		assertSimpleTime("October", "2005-10", ref);
		assertSimpleTime("March", "2006-03", ref);
		assertSimpleTime("April", "2005-04", ref);
	}

	@Test
	public void testSimpleMonthAndDay() throws Exception {
		DateTime ref = new DateTime(2000, 5, 5, 0, 0, 0, 0);

		assertSimpleTime("March 10", "2000-03-10", ref);
		assertSimpleTime("December 31", "2000-12-31", ref);

		ref = new DateTime(2000, 2, 6, 0, 0, 0, 0);
		assertSimpleTime("December 31", "1999-12-31", ref);
		assertSimpleTime("May 6", "2000-05-06", ref);

		ref = new DateTime(2000, 12, 1, 0, 0, 0, 0);
		assertSimpleTime("Jan 25", "2001-01-25", ref);
		assertSimpleTime("Oct 10", "2000-10-10", ref);
		assertSimpleTime("August 30", "2000-08-30", ref);
	}

	@Test
	public void testSimpleDayOfWeek() throws Exception {
		DateTime ref = new DateTime(2006, 7, 25, 0, 0, 0, 0);
		assertSimpleTime("Monday", "2006-07-24", ref);
		assertSimpleTime("Tuesday", "2006-07-25", ref);
		assertSimpleTime("Wednesday", "2006-07-19", ref);
		assertSimpleTime("Thursday", "2006-07-20", ref);
		assertSimpleTime("Friday", "2006-07-21", ref);
		assertSimpleTime("Saturday", "2006-07-22", ref);
		assertSimpleTime("Sunday", "2006-07-23", ref);

		assertSimpleTime("last Monday", "2006-07-24", ref);
		assertSimpleTime("last Tuesday", "2006-07-18", ref);
		assertSimpleTime("last Wednesday", "2006-07-19", ref);
		assertSimpleTime("last Thursday", "2006-07-20", ref);
		assertSimpleTime("last Friday", "2006-07-21", ref);
		assertSimpleTime("last Saturday", "2006-07-22", ref);
		assertSimpleTime("last Sunday", "2006-07-23", ref);

		assertSimpleTime("Last Monday", "2006-07-24", ref);
		assertSimpleTime("Last Tuesday", "2006-07-18", ref);
		assertSimpleTime("Last Wednesday", "2006-07-19", ref);
		assertSimpleTime("Last Thursday", "2006-07-20", ref);
		assertSimpleTime("Last Friday", "2006-07-21", ref);
		assertSimpleTime("Last Saturday", "2006-07-22", ref);
		assertSimpleTime("Last Sunday", "2006-07-23", ref);
	}

	@Test
	public void testSimpleLastMonth() throws Exception {
		DateTime ref = new DateTime(2006, 10, 1, 0, 0, 0, 0);
		assertSimpleTime("Last January", "2006-01", ref);
		assertSimpleTime("Last February", "2006-02", ref);
		assertSimpleTime("Last March", "2006-03", ref);
		assertSimpleTime("Last April", "2006-04", ref);
		assertSimpleTime("Last May", "2006-05", ref);
		assertSimpleTime("Last June", "2006-06", ref);
		assertSimpleTime("Last July", "2006-07", ref);
		assertSimpleTime("Last August", "2006-08", ref);
		assertSimpleTime("Last September", "2006-09", ref);
		assertSimpleTime("Last October", "2005-10", ref);
		assertSimpleTime("Last November", "2005-11", ref);
		assertSimpleTime("Last December", "2005-12", ref);
	}

	@Test
	public void testSimpleDigits() throws Exception {
		DateTime ref = new DateTime(2000, 5, 10, 0, 0, 0, 0);
		assertSimpleTime("2004/01/05", "2004-01-05", ref);
		assertSimpleTime("1999-10-05", "1999-10-05", ref);
		assertSimpleTime("20000404", "2000-04-04", ref);
	}

	@Test
	public void testSimpleCentury() throws Exception {
		DateTime ref = new DateTime(2000, 1, 1, 0, 0, 0, 0);
		assertSimpleTime("19th century", "19", ref);
		assertSimpleTime("the 20th century", "20", ref);
	}

	@Test
	public void testSimpleTimeWithTimeZone() throws Exception {
		DateTime ref = new DateTime(2004, 1, 6, 0, 0, 0, 0);
		assertSimpleTime("0915 GMT", "2004-01-06T09:15Z", ref);
	}

	@Test
	public void testSimpleTimeWithAMPM() throws Exception {
		DateTime ref = new DateTime(2004, 1, 6, 0, 0, 0, 0);
		assertSimpleTime("10.20 am", "2004-01-06T10:20", ref);
		assertSimpleTime("10.20 pm", "2004-01-06T22:20", ref);
		assertSimpleTime("10.20 AM", "2004-01-06T10:20", ref);
		assertSimpleTime("10.20 PM", "2004-01-06T22:20", ref);
	}

	@Test
	public void testMonthAndNextYear() throws Exception {
		DateTime ref = new DateTime(1999, 7, 7, 0, 0, 0, 0);
		assertSimpleTime("January next year", "2000-01", ref);
		assertSimpleTime("July next year", "2000-07", ref);
		assertSimpleTime("August next year", "2000-08", ref);
	}

	@Test
	public void testMonthAndLastYear() throws Exception {
		DateTime ref = new DateTime(1999, 7, 7, 0, 0, 0, 0);
		assertSimpleTime("January last year", "1998-01", ref);
		assertSimpleTime("July last year", "1998-07", ref);
		assertSimpleTime("August last year", "1998-08", ref);
	}

	@Test
	public void testNowAndTonight() throws Exception {
		assertSimpleTime("now", "2006-09-24", date(2006, 9, 24));
		assertSimpleTime("Now", "2006-09-24", date(2006, 9, 24));
		assertSimpleTime("now", "1990-01-01", date(1990, 1, 1));
		assertSimpleTime("Now", "1980-12-31", date(1980, 12, 31));
		assertSimpleTime("tonight", "2006-09-24", date(2006, 9, 24));
		assertSimpleTime("Tonight", "2006-09-24", date(2006, 9, 24));
		assertSimpleTime("tonight", "1990-01-01", date(1990, 1, 1));
		assertSimpleTime("Tonight", "1980-12-31", date(1980, 12, 31));
	}

	@Test
	public void testLastNight() throws Exception {
		assertSimpleTime("last night", "1982-10-01", date(1982, 10, 2));
		assertSimpleTime("Last night", "1970-05-03", date(1970, 5, 4));
	}

	@Test
	public void testNWeeksAgo() throws Exception {
		assertSimpleTime("two weeks ago", "1990-05-14", date(1990, 5, 28));
		assertSimpleTime("ten weeks ago", "1990-05-14", date(1990, 7, 23));
	}

	@Test
	public void testAWeekAgo() throws Exception {
		assertSimpleTime("a week ago", "1999-12-31", date(2000, 1, 7));
		assertSimpleTime("a week ago", "1999-12-24", date(1999, 12, 31));
		assertSimpleTime("A week ago", "1999-12-31", date(2000, 1, 7));
		assertSimpleTime("A week ago", "1999-12-24", date(1999, 12, 31));
	}

	@Test
	public void testNDaysAgo() throws Exception {
		assertSimpleTime("twenty days ago", "1980-03-01", date(1980, 3, 21));
		assertSimpleTime("twelve days ago", "1980-03-01", date(1980, 3, 13));
	}

	@Test
	public void testPastRef() throws Exception {
		assertAttributes("several years ago",
				"{VAL: PAST_REF, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 2000}", date(2000, 5, 5));
		assertAttributes("a few years ago",
				"{VAL: PAST_REF, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 2000}", date(2000, 5, 5));
		assertAttributes("a couple of years ago",
				"{VAL: PAST_REF, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 2000}", date(2000, 5, 5));

		assertAttributes("several months ago",
				"{VAL: PAST_REF, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 2000-05}", date(2000, 5, 5));
		assertAttributes("a few months ago",
				"{VAL: PAST_REF, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 2000-05}", date(2000, 5, 5));
		assertAttributes("a couple of months ago",
				"{VAL: PAST_REF, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 2000-05}", date(2000, 5, 5));

		assertAttributes("several weeks ago",
				"{VAL: PAST_REF, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 2000-05-05}", date(2000, 5, 5));
		assertAttributes("a few weeks ago",
				"{VAL: PAST_REF, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 2000-05-05}", date(2000, 5, 5));
		assertAttributes("a couple of weeks ago",
				"{VAL: PAST_REF, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 2000-05-05}", date(2000, 5, 5));

		assertAttributes("several days ago",
				"{VAL: PAST_REF, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 2000-05-05}", date(2000, 5, 5));
		assertAttributes("a few days ago",
				"{VAL: PAST_REF, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 2000-05-05}", date(2000, 5, 5));
		assertAttributes("a couple of days ago",
				"{VAL: PAST_REF, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 2000-05-05}", date(2000, 5, 5));
	}

	@Test
	public void testLastYears() throws Exception {
		assertAttributes("last years", "{VAL: PXY, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600 }", date(
				1600, 1, 1));
		assertAttributes("last few years", "{VAL: PXY, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600 }",
				date(1600, 1, 1));
		assertAttributes("last several years", "{VAL: PXY, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600 }",
				date(1600, 1, 1));
		assertAttributes("recent years", "{VAL: PXY, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600 }", date(
				1600, 1, 1));
		assertAttributes("for years", "{VAL: PXY, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600 }", date(
				1600, 1, 1));

		assertAttributes("last months", "{VAL: PXM, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01 }",
				date(1600, 1, 1));
		assertAttributes("last few months", "{VAL: PXM, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01 }",
				date(1600, 1, 1));
		assertAttributes("last several months",
				"{VAL: PXM, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01 }", date(1600, 1, 1));
		assertAttributes("recent months", "{VAL: PXM, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01 }",
				date(1600, 1, 1));
		assertAttributes("for months", "{VAL: PXM, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01 }",
				date(1600, 1, 1));

		assertAttributes("last weeks", "{VAL: PXW, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01-01 }",
				date(1600, 1, 1));
		assertAttributes("last few weeks",
				"{VAL: PXW, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01-01 }", date(1600, 1, 1));
		assertAttributes("last several weeks",
				"{VAL: PXW, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01-01 }", date(1600, 1, 1));
		assertAttributes("recent weeks", "{VAL: PXW, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01-01 }",
				date(1600, 1, 1));
		assertAttributes("for weeks", "{VAL: PXW, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01-01 }",
				date(1600, 1, 1));

		assertAttributes("last days", "{VAL: PXD, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01-01 }",
				date(1600, 1, 1));
		assertAttributes("last few days",
				"{VAL: PXD, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01-01 }", date(1600, 1, 1));
		assertAttributes("last several days",
				"{VAL: PXD, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01-01 }", date(1600, 1, 1));
		assertAttributes("recent days", "{VAL: PXD, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01-01 }",
				date(1600, 1, 1));
		assertAttributes("for days", "{VAL: PXD, ANCHOR_DIR: BEFORE, ANCHOR_VAL: 1600-01-01 }",
				date(1600, 1, 1));
	}

	@Test
	public void testComingYears() throws Exception {
		assertAttributes("coming years", "{ VAL: PXY, ANCHOR_DIR: AFTER, ANCHOR_VAL: 2000 }", date(
				2000, 8, 15));
		assertAttributes("next few years", "{ VAL: PXY, ANCHOR_DIR: AFTER, ANCHOR_VAL: 2000 }",
				date(2000, 8, 15));

		assertAttributes("coming months", "{ VAL: PXM, ANCHOR_DIR: AFTER, ANCHOR_VAL: 2000-08 }",
				date(2000, 8, 15));
		assertAttributes("next few months", "{ VAL: PXM, ANCHOR_DIR: AFTER, ANCHOR_VAL: 2000-08 }",
				date(2000, 8, 15));

		assertAttributes("coming weeks", "{ VAL: PXW, ANCHOR_DIR: AFTER, ANCHOR_VAL: 2000-08-15 }",
				date(2000, 8, 15));
		assertAttributes("next few weeks",
				"{ VAL: PXW, ANCHOR_DIR: AFTER, ANCHOR_VAL: 2000-08-15 }", date(2000, 8, 15));

		assertAttributes("coming days", "{ VAL: PXD, ANCHOR_DIR: AFTER, ANCHOR_VAL: 2000-08-15 }",
				date(2000, 8, 15));
		assertAttributes("next few days",
				"{ VAL: PXD, ANCHOR_DIR: AFTER, ANCHOR_VAL: 2000-08-15 }", date(2000, 8, 15));
	}

	@Test
	public void testISO8601Time() throws Exception {
		assertSimpleTime("2004-11-04T23:28:00", "2004-11-04T23:28:00", date(2006, 4, 5));
	}
	
	@Test
	public void testFullTime() throws Exception {
		assertSimpleTime("Fri, 05 Nov 2004 05:30:37 GMT", "2004-11-05T05:30:37Z", date(2005, 3, 2));
		assertSimpleTime("Fri, 5 Nov 2004 11:20:13 +1000", "2004-11-05T11:20:13+1000", date(2005, 3, 2));
	}
	
	@Test
	public void testOrdinalNumber() throws Exception {
		assertSimpleTime("the fourth of November", "2000-11-04", date(2000, 11, 1));
		assertSimpleTime("the fifteenth of January", "2000-01-15", date(1999, 10, 10));
		assertSimpleTime("the fifteenth of January", "2000-01-15", date(2000, 5, 10));
		assertSimpleTime("the first of November 1995", "1995-11-01", date(1995, 4, 3));
	}
	
	@Test
	public void testSimpleDuration() throws Exception {
		DateTime ref = new DateTime(2000, 1, 1, 0, 0, 0, 0);
		assertTime("<TIMEX2 VAL=\"P6M\">six months</TIMEX2>", ref);
		assertTime("<TIMEX2 VAL=\"P2M\">exactly two months</TIMEX2>", ref);
	}

	@Test
	public void testDuration() throws Exception {
		DateTime ref = new DateTime(2000, 4, 15, 0, 0, 0, 0);
		assertTime("<TIMEX2 VAL=\"P6M\" ANCHOR_DIR=\"ENDING\" ANCHOR_VAL=\"2000-03-31\" >"
				+ "six months ended " + "<TIMEX2 VAL=\"2000-03-31\">March 31</TIMEX2></TIMEX2>",
				ref);

		assertTime("<TIMEX2 VAL=\"P2D\" ANCHOR_DIR=\"ENDING\" ANCHOR_VAL=\"2000-04-15\">"
				+ "the past two days</TIMEX2>", ref);

		assertTime("<TIMEX2 VAL=\"PT3H\">three hours</TIMEX2>", ref);
		assertTime("<TIMEX2 VAL=\"PT4H\">four minutes</TIMEX2>", ref);
		assertTime("<TIMEX2 VAL=\"PT5S\">five seconds</TIMEX2>", ref);
	}

	@Test
	public void testThousand() throws Exception {
		Document doc = new Document("thousand");
		Tokenizer.tokenize(doc, doc.fullSpan());
		numberAnnotator.annotate(doc);
		timeAnnotator.annotate(doc, doc.fullSpan(), date(2005, 1, 1));

		List<Annotation> times = doc.annotationsOfType("TIMEX2");
		assertNull(times);
	}

	private void assertSimpleTime(String text, String val, DateTime ref) throws Exception {
		Document doc = new Document(text);
		Tokenizer.tokenize(doc, doc.fullSpan());
		numberAnnotator.annotate(doc, doc.fullSpan());
		timeAnnotator.annotate(doc, doc.fullSpan(), ref);

		List<Annotation> times = doc.annotationsOfType("TIMEX2");
		try {
			assertNotNull(times);
			assertEquals(1, times.size());
			assertEquals(doc.fullSpan(), times.get(0).span());
			assertEquals(val, times.get(0).get("VAL"));
		} catch (AssertionError e) {
			System.err.println(doc.writeSGML(null));
			throw e;
		}
	}

	private void assertAttributes(String text, String attrString, DateTime ref) throws Exception {
		Document doc = new Document(text);
		Tokenizer.tokenize(doc, doc.fullSpan());
		numberAnnotator.annotate(doc, doc.fullSpan());
		timeAnnotator.annotate(doc, doc.fullSpan(), ref);

		Map<String, Object> expectedAttrs = loadYaml(attrString);

		List<Annotation> times = doc.annotationsOfType("TIMEX2");
		assertNotNull(times);
		assertEquals(1, times.size());
		assertEquals(doc.fullSpan(), times.get(0).span());

		Map<String, ?> actualAttrs = featureSetToMap(times.get(0).attributes());
		assertEquals(expectedAttrs, actualAttrs);
	}

	private void assertTime(String text, DateTime ref) throws Exception {
		Document expectedDoc = parse(text);
		Document doc = new Document(expectedDoc.text());

		Tokenizer.tokenize(doc, doc.fullSpan());
		numberAnnotator.annotate(doc, doc.fullSpan());
		timeAnnotator.annotate(doc, doc.fullSpan(), ref);

		try {
			List<Annotation> expected = expectedDoc.annotationsOfType("TIMEX2");
			List<Annotation> actual = doc.annotationsOfType("TIMEX2");
			assertNotNull(actual);
			assertEquals(expected.size(), actual.size());
			Comparator<Annotation> cmp = new Comparator<Annotation>() {
				public int compare(Annotation a, Annotation b) {
					return a.span().compareTo(b.span());
				}
			};

			Collections.sort(expected, cmp);
			Collections.sort(actual, cmp);

			for (int i = 0; i < expected.size(); i++) {
				assertEquals(expected.get(i).span(), actual.get(i).span());
			}
		} catch (AssertionError error) {
			System.out.println("expetcted: " + expectedDoc.writeSGML("TIMEX2"));
			System.out.println("expetcted: " + doc.writeSGML("TIMEX2"));
			throw error;
		}
	}

	private static Document parse(String text) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder parser = factory.newDocumentBuilder();
		StringReader reader = new StringReader("<text>" + text + "</text>");
		InputSource source = new InputSource(reader);
		org.w3c.dom.Document doc = parser.parse(source);

		Document result = new Document(doc.getDocumentElement().getTextContent());
		annotateSGML(result, doc.getDocumentElement(), 0);

		return result;
	}

	private static void annotateSGML(Document doc, Element root, int offset) {
		NodeList children = root.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);

			if (child.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}

			Span span = getSpan(root, (Element) child, offset);
			FeatureSet attrs = new FeatureSet();
			NamedNodeMap map = child.getAttributes();
			for (int j = 0; j < map.getLength(); j++) {
				Attr attr = (Attr) map.item(j);
				attrs.put(attr.getName(), attr.getValue());
			}

			doc.annotate(((Element) child).getTagName(), span, attrs);
			annotateSGML(doc, (Element) child, span.start());
		}
	}

	private static Span getSpan(Element parent, Element element, int start) {
		int offset = 0;
		NodeList children = parent.getChildNodes();

		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child == element) {
				String innerText = element.getTextContent();
				return new Span(start + offset, start + offset + innerText.length());
			}

			switch (child.getNodeType()) {
			case Node.ELEMENT_NODE:
				offset += element.getTextContent().length();
				break;

			case Node.TEXT_NODE:
				offset += ((Text) child).getTextContent().length();
				break;

			default:
				throw new InternalError();
			}
		}

		return null;
	}

	private static DateTime date(int year, int month, int day) {
		return new DateTime(year, month, day, 0, 0, 0, 0);
	}

	private static Map<String, ?> featureSetToMap(FeatureSet fs) {
		Map<String, Object> map = new HashMap<String, Object>();
		Enumeration<?> e = fs.keys();
		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			map.put(key, fs.get(key));
		}

		return map;
	}

	private static Map<String, Object> loadYaml(String yaml) {
		Map<String, Object> map = (Map<String, Object>) Yaml.load("--- " + yaml);

		for (String key : map.keySet()) {
			map.put(key, map.get(key).toString());
		}

		return map;
	}
}
