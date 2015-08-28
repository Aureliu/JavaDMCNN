// -*- tab-width: 4 -*-
// to fix:  write charseq ... XML
package AceJet;

//Author:       Ralph Grishman
//Date:         May 18, 2008

import java.util.*;
import java.io.*;
import Jet.Tipster.*;

/**
 *  contains code to write supplementary syntactic information into the
 *  APF file.
 */

public class AcePlus {
	
	static int nodeNo;

	/**
	 *  write sentence, token, and parse tree information on Document
	 *  <CODE>doc</CODE> on PrintWriter <CODE>w</CODE>.
	 */

	static void write (Document doc, PrintWriter w) {
		// iterate over sentences
		Vector<Annotation> sentences = doc.annotationsOfType ("sentence");
		if (sentences == null)
			return;
		int sentNo = 0;
		for (Annotation sentence : sentences) {
			sentNo++;
			Span sentenceSpan = new Span(sentence.span().start(), sentence.span().end());
			doc.shrink(sentence);
			Span shrunkSentenceSpan = sentence.span();
			// get ID
			String sentenceID = (String) sentence.get("ID");
			if (sentenceID == null) {
				sentenceID = "S." + sentNo;
				System.out.println ("Generating sentence ID " + sentenceID);
			}
			// get SOURCESENTID (for MT output)
			String sourceSentIdFeature = "";
			String sourceSentID = (String) sentence.get("SOURCESENTID");
			if (sourceSentID != null)
				sourceSentIdFeature = " SOURCESENTID=\"" + sourceSentID + "\"";
			// write sentence tag
			w.println ("  <sentence ID=\"" + sentenceID + "\"" + sourceSentIdFeature + ">");
			w.println ("    <charseq START=\"" + shrunkSentenceSpan.start() + "\"" +
			                       " END=\"" + (shrunkSentenceSpan.end()-1) + "\"></charseq>");
			// iterate over tokens:  write token tag
			Vector<Annotation> tokens = doc.annotationsOfType ("token", sentenceSpan);
			int tokenNo = 0;
			if (tokens != null) {
				for (Annotation token : tokens) {
					tokenNo++;
					doc.shrink(token);
					Span tokenSpan = token.span();
					Span aceTokenSpan = new Span(tokenSpan.start(), tokenSpan.end()-1);
					w.println ("    <token ID=\"" + sentenceID + "." + tokenNo + "\">");
					AceEntityMention.writeCharseq (w, aceTokenSpan, doc.text(tokenSpan));
					w.println ("    </token>");
				}
			}
			// if parse, get root, write parse node (root, 0)
			Annotation root = (Annotation) sentence.get("parse");
			if (root != null) {
				w.println ("    <parse>");
				nodeNo = 0;
				writeParseNode (doc, root, 0, sentNo, w);
				w.println ("    </parse>");
			}
			w.println ("  </sentence>");
		}
	}
	
	static void writeParseNode (Document doc, Annotation node, int level, int sentNo, PrintWriter w) {
		if (node == null) return;
		// write <node>
		nodeNo++;
		w.print ("      ");
		for (int i=0; i<level; i++) w.print("  ");
		String cat = (String) node.get("cat");
		String id = "N-" + sentNo + "-" + nodeNo;
		w.println ("<node cat=\"" + cat + "\" ID=\"" + id + "\">");
		// if node has children,
		Annotation[] children = (Annotation[]) node.get("children");
		if (children != null) {
		//   iterate over children, write parse node
			for (Annotation child : children)
				writeParseNode (doc, child, level+1, sentNo, w);
		} else {
		//   else write charseq
			for (int i=0; i<level; i++) w.print("  ");
			doc.shrink(node);
			Span aceNodeSpan = new Span(node.start(), node.end()-1);
			AceEntityMention.writeCharseq (w, aceNodeSpan, doc.text(node));
		}
		// write </node>
		w.print ("      ");
		for (int i=0; i<level; i++) w.print("  ");
		w.println ("</node>");		
	}

}		