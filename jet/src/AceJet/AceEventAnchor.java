// -*- tab-width: 4 -*-
//Title:        JET
//Copyright:    2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkil
//              (ACE extensions)

package AceJet;

import java.util.*;
import Jet.Tipster.*;
import Jet.Refres.Resolve;

public class AceEventAnchor extends AceMention {

	Span head;

	Span jetHead;

	boolean passive = false;

	public AceEventAnchor (Span head, Span jetHead, String text, Document doc) {
		this.head = head;
		this.jetHead = jetHead;
		this.extent = head;
		this.jetExtent = jetHead;
		this.text = text;
		computeJetExtent (jetHead, doc);
	}

	public AceEventArgumentValue getParent () {return null;};

	public String getType () {return null;};

	public Span getJetHead() {
		return jetHead;
	}

	int NP_SEARCH_WINDOW = 40;
	
	/**
	 *  given the span of the head, attempts to find an enclosing constituent
	 *  and uses that constituent as the extent of the anchor.  This constituent
	 *  can either be a 'vg' or 'vg-pass' constituent ending at the anchor, or
	 *  a 'np' whose head is the anchor.
	 */

	private void computeJetExtent (Span jetHead, Document doc) {
		Vector constits = doc.annotationsEndingAt (jetHead.end(), "constit");
		if (constits != null) {
			for (int i=0; i<constits.size(); i++) {
				Annotation ann = (Annotation) constits.get(i);
				if (ann.get("cat") == "vg") {
					jetExtent = ann.span();
					// System.out.println (">>> extending anchor to " + doc.text(jetExtent));
					return;
				} else if (ann.get("cat") == "vg-pass") {
					jetExtent = ann.span();
					passive = true;
					// System.out.println (">>> extending anchor to " + doc.text(jetExtent));
					return;
				}
			}
		}
		int headStart = jetHead.start();
		int begin = headStart < NP_SEARCH_WINDOW ? 0 : headStart - NP_SEARCH_WINDOW;
		for (int k = begin; k <= headStart; k++) {
			constits = doc.annotationsAt (k, "constit");
			if (constits == null) return;
			for (int i=0; i<constits.size(); i++) {
				Annotation ann = (Annotation) constits.get(i);
				if (ann.get("cat") != "np") continue;
				Annotation headC = Resolve.getHeadC(ann);
				if (headC.span().start() != jetHead.start()) continue;
				if (ann.span().within(jetExtent)) continue;
				jetExtent = ann.span();
				// System.out.println (">>> extending anchor to " + doc.text(jetExtent));
			}
		}
	}
}
