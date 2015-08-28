// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.30
//Copyright:    Copyright (c) 2005
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Toolkit

package Jet.Refres;
import java.util.*;
import Jet.Tipster.*;
import Jet.Lisp.*;

/**
 *  CorefFilter provides a set of static methods for converting between
 *  alternative representations of coreference information.
 *  <p>
 *  The standard representation of coreference used internally by Jet is a
 *  set of 'entity' Annotations, where each 'entity' annotation has a
 *  'mentions' feature.  The value of the 'mentions' feature is an array
 *  of Annotations, where each Annotation is of type 'mention' or 'constit'.
 *  The mentions in the array are coreferential.
 *  <p>
 *  Such a representation using Annotations pointing to other Annotations
 *  is not convenient for external (XML) representation.  Two other
 *  representations are used externally, a numbered-entity representation
 *  and a linked-mention representation.
 *  <p>
 *  The linked-mention representation was used as the standard for MUC-6
 *  and MUC-6.  Each mention is marked by an Annotation <br>
 *  &lt; COREF ID="n1" REF="n2" &gt; <br>
 *  indicating that the mention with ID n2 is the antecedent of the mention
 *  with ID n1.
 *  <p>
 *  In the numbered-entity representation, each mention is marked by
 *  an Annotation <br>
 *  &lt; mention entity=entity-ID :gt; <br>
 *  where mentions with the same entity-ID are coreferential.
 */

public class CorefFilter {

  /**
	 *  buildEntitiesFromMentions takes a Document with coreference marked
	 *  in numbered-entity form, with Annotations
	 *  of the form <br>
	 *      mention entity=entity-ID                                <br>
	 *  where coreferential entities share the same entity-ID, and generates
	 *  annotations of the form                                     <br>
	 *      entity mentions=Vector(mentions)                        <br>
	 */

	public static void buildEntitiesFromMentions (Document doc) {
		HashMap entityIdToMentions = new HashMap();
		Vector mentions = doc.annotationsOfType("mention");
		for (int i=0; i<mentions.size(); i++) {
			Annotation mention = (Annotation) mentions.get(i);
			Object entityId = mention.get("entity");
			if (entityId == null) {
				System.err.println ("CorefEval.buildEntities:  mention annotation without entity id");
			} else {
				Vector mentionList = (Vector) entityIdToMentions.get(entityId);
				if (mentionList == null)
					mentionList = new Vector();
				mentionList.add(mention);
				entityIdToMentions.put(entityId, mentionList);
			}
		}
		Set entityIds = entityIdToMentions.keySet();
		Iterator it = entityIds.iterator();
		while (it.hasNext()) {
			String entityId = (String) it.next();
			Vector mentionList = (Vector) entityIdToMentions.get(entityId);
			Annotation firstMention = (Annotation) mentionList.get(0);
			doc.annotate("entity", firstMention.span(), new FeatureSet("mentions", mentionList));
		}
	}

	private static HashMap idToMention, mentionToEntity;

  /**
	 *  buildEntitiesFromLinkedMentions takes a Document with annotations
	 *  following the MUC standard (linked-mention representation)  <br>
	 *      coref id=mention-ID ref=prior-mention-ID                <br>
	 *  and generates the internal representation of coreference,
	 *  with annotations of the form                                <br>
	 *      entity mentions=Vector(mentions)                        <br>
	 */

	public static void buildEntitiesFromLinkedMentions (Document doc) {
		Vector corefs = doc.annotationsOfType("coref");
		if (corefs == null) return;
		idToMention = new HashMap();
		for (int i=0; i<corefs.size(); i++) {
			Annotation coref = (Annotation) corefs.get(i);
			Object id = coref.get("id");
			if (id == null) {
				System.err.println ("(buildEntitiesFromRefs) coref annotation without id");
			} else {
				idToMention.put(id, coref);
			}
		}
		mentionToEntity = new HashMap();
		for (int i=0; i<corefs.size(); i++) {
			Annotation mention = (Annotation) corefs.get(i);
			mentionToEntity(mention, doc, 0);
		}
	}

	private static Annotation mentionToEntity (Annotation mention, Document doc, int level) {
		if (level > 100) {
			System.err.println ("(mentionToEntity) loop of REF pointers");
			return null;
		}
		Annotation entity = (Annotation) mentionToEntity.get(mention);
		if (entity != null)
			return entity;
		String ref = (String) mention.get("ref");
		if (ref == null) {
			Vector v = new Vector();
			v.add(mention);
			entity =
			    new Annotation("entity", mention.span(), new FeatureSet ("mentions", v));
			doc.addAnnotation(entity);
		} else {
			Annotation referent = (Annotation) idToMention.get(ref);
			if (referent == null)
				System.err.println ("(mentionToEntity) undefined REF pointer" + ref);
			entity = mentionToEntity (referent, doc, level+1);
			Vector v = (Vector) entity.get("mentions");
			v.add(mention);
		}
		mentionToEntity.put(mention, entity);
		return entity;
	}

	/**
	 *  buildMentionsFromEntities takes a Document with coreference information
	 *  in the form of mention attributes on entities and generates Annotations
	 *  of the form <br>
	 *      mention entity=entity-ID                                <br>
	 *  over the <I>heads</I> of mentions, where coreferential mentions are
	 *  linked by having the same entity-ID.
	 */

	public static void buildMentionsFromEntities (Document doc) {
		Vector entities = doc.annotationsOfType("entity");
		for (int i=0; i<entities.size(); i++) {
			Annotation entity = (Annotation) entities.get(i);
			Integer entityID = new Integer(i);
			Vector mentions = (Vector) entity.get("mentions");
			for (int j=0; j<mentions.size(); j++) {
				Annotation mention = (Annotation) mentions.get(j);
				Annotation mentionHead = Resolve.getHeadC(mention);
				if (mentionHead.type() != "mention") {
					mentionHead = new Annotation ("mention", mentionHead.span(), null);
					doc.addAnnotation(mentionHead);
				}
				mentionHead.put("entity", entityID);
			}
		}
	}

	/**
	 *  buildLinkedMentionsFromEntities takes a Document with coreference information
	 *  in the form of mention attributes on entities and generates Annotations
	 *  of the form <br>
	 *      COREF ID="n1" REF="n2"                                <br>
	 *  over the <I>heads</I> of mentions, where coreferential mentions are
	 *  linked by having the same entity-ID.
	 */

	public static void buildLinkedMentionsFromEntities (Document doc) {
		int mentionID = 0;
		Vector entities = doc.annotationsOfType("entity");
		for (int i=0; i<entities.size(); i++) {
			Annotation entity = (Annotation) entities.get(i);
			Vector mentions = (Vector) entity.get("mentions");
			int antecedent = 0;
			for (int j=0; j<mentions.size(); j++) {
				Annotation mention = (Annotation) mentions.get(j);
				Annotation mentionHead = Resolve.getHeadC(mention);
				if (mentionHead.type() != "COREF") {
					mentionHead = new Annotation ("COREF", mentionHead.span(), null);
					doc.addAnnotation(mentionHead);
				}
				mentionHead.put("ID", new Integer(++mentionID));
				if (antecedent > 0)
					mentionHead.put("REF", new Integer(antecedent));
				antecedent = mentionID;
			}
		}
	}
}
