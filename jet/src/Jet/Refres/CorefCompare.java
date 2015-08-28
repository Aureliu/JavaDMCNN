// -*- tab-width: 4 -*-
package Jet.Refres;

import java.util.*;
import Jet.Tipster.*;
import Jet.Lisp.FeatureSet;

/**
 *  static methods to compare entity annotations (coreference) in
 *  two documents or collections.
 *  <br>
 *  These methods add 'status' attribute to entities in the
 *  response Document(s) which can then be used by entityView.
 */

public class CorefCompare {

	/**
	 *  compare the entity annotations (coreference) in Documents
	 *  'response' and 'key', updating Document 'response'.  The two
	 *  Documents should be different annotations of the same text.
	 *  <p>
	 *  Two mentions are considered the same if their spans (or the
	 *  spans of their heads, if they have headC attributes) are the same.
	 *  Two entities are considered the same if they have the same set
	 *  of mentions ("mentions" attribute).
	 *  <p>
	 *  There are three cases:
	 *  <ul><li>
	 *  an entity in response has a corresponding entity in key;  update
	 *  the entity in response with 'status=OK'.
	 *  <li>
	 *  an entity in response has no corresponding entity in key;  update
	 *  the entity in response with 'status=response'.
	 *  <li>
	 *  an entity in key has no corresponding entity in response;  add
	 *  a corresponding entity to Document 'response' (adding mentions
	 *  if necessary) and mark the entity 'status=key'.
	 *  </ul>
	 *  In addition, if there are <I>any</I> differences between the
	 *  entities in the two documents, the annotation 'entitiesDiffer'
	 *  is added to Document 'response'.
	 */

	public static void compareDocuments (Document response, Document key) {
		// should check that documents are same size *********
		boolean different = false;
		HashMap mentionMap = new HashMap();
		Vector keyMentions = CorefScorer.findMentions(key);
		Vector responseMentions = CorefScorer.findMentions(response);
		// loop over all mentions in key
	keyLoop:
		for (int i=0; i<keyMentions.size(); i++) {
			Annotation keyMention = (Annotation) keyMentions.get(i);
			Span keySpan = keyMention.span();
			// is there a mention in the response with the same span?
			for (int j=0; j<responseMentions.size(); j++) {
				Annotation responseMention = (Annotation) responseMentions.get(j);
				Annotation responseMentionHead = Resolve.getHeadC(responseMention);
				if (responseMentionHead.span().equals(keySpan)) {
					// yes, mark it status=OK
					mentionMap.put(keyMention, responseMention);
					responseMention.attributes().put("status", "OK");
					continue keyLoop;
				}
			}
			// no, add mention to response with feature status=key
			Annotation responseMention = new Annotation
				("mention", keySpan, new FeatureSet("status", "key"));
			response.addAnnotation(responseMention);
			mentionMap.put(keyMention, responseMention);
			different = true;
		}
		// mark remaining mentions in response as status=response
		for (int j=0; j<responseMentions.size(); j++) {
			Annotation responseMention = (Annotation) responseMentions.get(j);
			if (responseMention.get("status") == null) {
				responseMention.attributes().put("status", "response");
				different = true;
			}
		}

		Vector keyEntities = key.annotationsOfType("entity");
		Vector responseEntities = response.annotationsOfType("entity");
		// loop over entities in key
	entityLoop:
		for (int i=0; i<keyEntities.size(); i++) {
			Annotation keyEntity = (Annotation) keyEntities.get(i);
			Vector keyEntityMentions = (Vector) keyEntity.get("mentions");
			// is there an entity in response with the same mentions?
		entityLoop2:
			for (int j=0; j<responseEntities.size(); j++) {
				Annotation responseEntity = (Annotation) responseEntities.get(j);
				Vector responseEntityMentions = (Vector) responseEntity.get("mentions");
				if (keyEntityMentions.size() != responseEntityMentions.size())
					continue entityLoop2;
				for (int k=0; k<keyEntityMentions.size(); k++) {
					Annotation keyMention = (Annotation) keyEntityMentions.get(k);
					boolean foundResponseMention = false;
					for (int kk=0; kk<responseEntityMentions.size(); kk++) {
						Annotation responseMention = (Annotation) responseEntityMentions.get(kk);
						if (mentionMap.get(keyMention) == responseMention) {
							foundResponseMention = true;
							break;
						}
					}
					if (!foundResponseMention)
						continue entityLoop2;
				}
				// yes, mark response entity status=OK
				responseEntity.put("status", "OK");
				continue entityLoop;
			}
			// no, add entity to response with feature status = key
			//   first, build mentions vector
			Vector m = new Vector();
			for (int k=0; k<keyEntityMentions.size(); k++) {
				Annotation keyMention = (Annotation) keyEntityMentions.get(k);
				Annotation responseMention = (Annotation) mentionMap.get(keyMention);
				m.add(responseMention);
			}
			Annotation responseEntity = new Annotation
				("entity", keyEntity.span(), new FeatureSet("mentions", m, "status", "key"));
			response.addAnnotation(responseEntity);
			different = true;
		}
		// mark remaining entities as status=response
		for (int j=0; j<responseEntities.size(); j++) {
			Annotation responseEntity = (Annotation) responseEntities.get(j);
			if (responseEntity.get("status") == null) {
				responseEntity.attributes().put("status", "response");
				different = true;
			}
		}
		if (different) {
			Annotation entitiesDiffer = new Annotation	("entitiesDiffer",
				new Span(0,0), null);
			response.addAnnotation(entitiesDiffer);
		}
	}

	/**
	 *  compare the entity annotations (coreference) in all documents in
	 *  Document Collections 'response' and 'key', updating the documents
	 *  in DocumentCollection 'response'.  The two collections should
	 *  be the same size and have Documents which are different annotations
	 *  of the same text.
	 */

	public static void compareCollections (DocumentCollection response,
	                                       DocumentCollection key) {
	  int responseSize = response.size();
	  int keySize = key.size();
	  if (responseSize != keySize) {
	  	System.out.println ("CorefCompare.compareCollections:  collections of different sizes, can't compare.");
			return;
		}
		for (int i=0; i<keySize; i++) {
			compareDocuments (response.get(i), key.get(i));
		}
	}
}
