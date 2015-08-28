// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.10
//Copyright:    Copyright (c) 2003
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool

package Jet;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.StringTokenizer;
import java.util.Vector;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;

import Jet.Chunk.Chunker;
import Jet.Chunk.Onoma;
import Jet.Lex.Lexicon;
import Jet.Lex.Tokenizer;
import Jet.Parser.AddSyntacticRelations;
import Jet.Parser.ParseTreeNode;
import Jet.Parser.Parsers;
import Jet.Parser.StatParser;
import Jet.Parser.DepParser;
import Jet.Refres.CorefFilter;
import Jet.Refres.EntityView;
import Jet.Refres.Resolve;
import Jet.Tipster.Annotation;
import Jet.Tipster.Document;
import Jet.Tipster.ExternalDocument;
import Jet.Tipster.Span;
import Jet.Tipster.View;
import Jet.Zoner.SentenceSplitter;
import Jet.Zoner.SpeechSplitter;

/**
 * the Control class provides the methods for interpreting Jet scripts.
 */

public class Control {

	/**
	 * apply the <B>processDocument</B> script to (all of) document <CODE>doc</CODE>.
	 * In addition, if Jet parameter WriteSGML.type is set, write the document
	 * in SGML format to file response- <CODE>inputFileName</CODE>. If <CODE>viewable</CODE>
	 * is true, open a window displaying the document, and label the window "Jet
	 * Document <CODE>docNo</CODE>".
	 */

	public static void processDocument(Document doc, BufferedWriter writer, boolean viewable,
			int docNo) throws IOException {

		String script = JetTest.config.getProperty("processDocument");
		if (script == null || script.length() == 0) {
			Console.println("*** System error: no processDocument script.");
			return;
		}
		// if there is a name tagger, clear its cache
		if (JetTest.nameTagger != null)
			JetTest.nameTagger.newDocument();
		applyScript(doc, new Span(0, doc.length()), script);
		String type = JetTest.config.getProperty("WriteSGML.type");
		if (type != null) {
			System.out.println("Writing document " + docNo);
			String sgml = toSGML(doc, type);
			ExternalDocument.writeWithSystemNewlines(writer, sgml);
			writer.newLine();
		}
		if (viewable) {
			View view = new View(doc, docNo);
			JetTest.views.add(view);
			Vector v = doc.annotationsOfType("entity");
			if (v != null && v.size() > 0) {
				EntityView eview = new EntityView(doc, docNo);
				JetTest.views.add(eview);
			}
		}
	}

	/**
	 * apply the <B>processSentence</B> script to span <CODE>sentenceSpan</CODE>
	 * of document <CODE>doc</CODE>.
	 */

	public static void processSentence(Document doc, Span sentenceSpan) {
		String script = JetTest.config.getProperty("processSentence");
		if (script == null || script.length() == 0)
			Console.println("*** No processSentence script.");
		else
			Control.applyScript(doc, sentenceSpan, script);
	}

	/**
	 * apply script <CODE>script</CODE> to span <CODE>span</CODE> of
	 * document <CODE>doc</CODE>.
	 */

	public static void applyScript(Document doc, Span span, String script) {
		String[] actions = splitAtComma(script);

		for (int j = 0; j < actions.length; j++) {
			String action = actions[j].intern();

			int colon = action.indexOf(':');
			if (colon > 0 && colon < action.length() - 1) {
				String zoneType = action.substring(0, colon).trim();
				String scriptName = action.substring(colon + 1).trim();
				String zoneScript = JetTest.config.getProperty(scriptName);
				if (zoneScript == null || zoneScript.length() == 0) {
					Console.println("*** No script for " + scriptName);
					continue;
				}
				Vector zones = doc.annotationsOfType(zoneType, span);
				if (zones == null) {
					// Console.println("*** No annotations of type " + zoneType);
					continue;
				}
				for (int i = 0; i < zones.size(); i++) {
					Annotation zone = (Annotation) zones.get(i);
					applyScript(doc, zone.span(), zoneScript);
				}
			} else if (action.startsWith("tag(") && action.endsWith(")")) {
				String tagName = action.substring(4, action.length() - 1).trim();
				doc.annotateWithTag(tagName);
			} else if (action.startsWith("shrink(") && action.endsWith(")")) {
				String tagName = action.substring(7, action.length() - 1).trim();
				doc.shrink(tagName);
			} else if (action.startsWith("erase(") && action.endsWith(")")) {
				String tagName = action.substring(6, action.length() - 1).trim();
				doc.removeAnnotationsOfType(tagName);
			} else if (action == "tokenize") {
				Tokenizer.tokenize(doc, span);
			} else if (action == "sentenceSplit") {
				SentenceSplitter.split(doc, span);
			} else if (action == "speechSplit") {
				SpeechSplitter.split(doc, span);
			} else if (action == "lexLookup") {
				Lexicon.annotateWithDefinitions(doc, span.start(), span.end());
			} else if (action == "tagPOS") {
				if (JetTest.tagger == null)
					Console.println("Error:  no POS model loaded");
				else
					JetTest.tagger.tagPenn(doc, span);
			} else if (action == "tagJet") {
				if (JetTest.tagger == null)
					Console.println("Error:  no POS model loaded");
				else
					JetTest.tagger.tagJet(doc, span);
			} else if (action == "pruneTags") {
				if (JetTest.tagger == null)
					Console.println("Error:  no POS model loaded");
				else
					JetTest.tagger.prune(doc, span);
			} else if (action == "tagNames") {
				if (JetTest.nameTagger == null)
					Console.println("Error:  no name model loaded");
				else
					JetTest.nameTagger.tag(doc, span);
			} else if (action == "tagNamesFromOnoma") {
				if (!Onoma.loaded)
					Console.println("Error:  no onoma file loaded");
				else
					Onoma.tagNames (doc, span);
			} else if (action == "chunk") {
				if (Chunker.model == null)
					Console.println("Error:  no chunker model loaded");
				else
					Chunker.chunk(doc, span);
			} else if (action == "parse") {
				Vector parses = Parsers.parse(doc, 0, doc.length(), JetTest.gram);
				for (int i = 0; i < parses.size(); i++) {
					ParseTreeNode parse = (ParseTreeNode) parses.elementAt(i);
					if (parse != null) {
						ParseTreeNode.makeParseAnnotations(doc, parse);
						if (parses.size() == 1) {
							Console.println("Parse:");
						} else {
							Console.println("Parse " + (i + 1) + ":");
						}
						parse.printTree();
					}
				}
			} else if (action == "statParse") {
				if (!StatParser.isInitialized())
					Console.println("Error:  no grammar for parser");
				else
					StatParser.parse(doc, span);
			} else if (action == "depParse") {
				DepParser.parseSentence(doc, span, doc.relations);
			} else if (action == "syntacticRelations") {
				AddSyntacticRelations.annotate(doc, span);
			} else if (action.startsWith("pat(") && action.endsWith(")")) {
				String patternSetName = action.substring(4, action.length() - 1).trim();
				JetTest.pc.apply(patternSetName, doc, span);
			} else if (action == "resolve") {
				Resolve.references(doc, span);
				// note these two always apply to entire document
			} else if (action == "mentions(coindexed)") {
				CorefFilter.buildMentionsFromEntities(doc);
			} else if (action == "mentions(linked)") {
				CorefFilter.buildLinkedMentionsFromEntities(doc);
			} else if (action == "tagENE") {
				JetTest.extendedNameTagger.annotate(doc, span);
			} else if (action == "tagTimex") {
				tagTimex(doc, span);
			} else if (action == "setReferenceTime") {
				setReferenceTime(doc, span);
			} else {
				System.out.println("Unknown Jet.processSentence action: " + action);
			}

		}
	}

	private static String[] splitAtComma(String str) {
		StringTokenizer tok = new StringTokenizer(str, ",");
		int tokenCount = tok.countTokens();
		String[] result = new String[tokenCount];
		for (int i = 0; i < tokenCount; i++)
			result[i] = tok.nextToken().trim();
		return result;
	}

	/**
	 * Converts to SGML from Document Object.
	 *
	 * @param doc
	 *            document to be converted
	 * @param type
	 *            output tag types which is separated by commas.
	 * @return converted string
	 */
	private static String toSGML(Document doc, String type) {
		if (type.equalsIgnoreCase("all")) {
			return doc.writeSGML(null).toString();
		} else {
			String[] types = splitAtComma(type);
			if (types.length == 1) {
				return doc.writeSGML(types[0]).toString();
			}

			Document copied = new Document(doc.text());
			for (String annType : types) {
				Vector anns = doc.annotationsOfType(annType);
				if (anns == null) {
					continue;
				}

				for (Object o : anns) {
					copied.addAnnotation((Annotation) o);
				}
			}
			return copied.writeSGML(null).toString();
		}
	}

	/**
	 * Sets reference time for annotating TIMEX.
	 *
	 * @param doc
	 *            target document
	 * @param span
	 *            span that reference time is described
	 */
	private static void setReferenceTime(Document doc, Span span) {
		String str = doc.normalizedText(span);
		DateTimeFormatter format = JetTest.getReferenceTimeFormat();
		if (format == null) {
			System.err.println("setRefernceTime requires Timex.refFormat");
			return;
		}

		DateTime ref = format.parseDateTime(str);
		JetTest.setReferenceTime(ref);
	}

	/**
	 * Annotates TIMEX tags.
	 *
	 * @param doc
	 *            target document
	 * @param span
	 *            span to be annotated
	 */
	private static void tagTimex(Document doc, Span span) {
		DateTime ref = JetTest.getReferenceTime();
		if (ref == null) {
			System.err.println("tagTimex requires refernce time.");
			return;
		}

		JetTest.getNumberAnnotator().annotate(doc, span);
		JetTest.getTimeAnnotator().annotate(doc, span, ref);
	}
}
