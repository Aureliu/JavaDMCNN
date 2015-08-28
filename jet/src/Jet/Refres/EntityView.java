// -*- tab-width: 4 -*-
package Jet.Refres;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.util.*;
import Jet.Lisp.*;
import Jet.Tipster.*;
import Jet.JetTest;

/**
 *  Displays entities and their mentions.  Generates a display of a
 *  Document and a list of the entities contained in it.  Allows a user
 *  to select an entity, highlights the associated mentions, and connects
 *  the mentions by lines.  <P>
 *    If a Document has been processed by CorefCompare, the tags added
 *  by CorefCompare are reflected in the color of the displayed entities
 *  (differentiating key and response entities).
 */

public class EntityView extends Jet.Tipster.View {

	private Object[] entities;

	/**
	 *  Creates an EntityView for Document <CODE>doc</CODE>.  The document
	 *  number <CODE>docNo</CODE> is used to label the window.
	 */

	public EntityView(Jet.Tipster.Document doc, int docNo) {
	    document = doc;
	    try {
	    	Vector v = document.annotationsOfType("entity");
		    if (v == null || v.size() == 0) {
		    	System.err.println (" (EntityView) No entities to display.");
		    	return;
		    }
		    Annotation.sort(v);
		    Collections.reverse(v);
		    entities = v.toArray();
		    String[] entityDescriptions = new String[entities.length];
		    for (int i=0; i<entities.length; i++)
		    	entityDescriptions[i] = entityDescription((Annotation) entities[i]);
				jbInit(entityDescriptions, "Entities");
				this.setTitle("Entities for Document " + docNo);

				// Cascades windows
				Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
				Dimension frameSize = this.getSize();
				int n = (screenSize.width - frameSize.width) / 30;
				// position is slightly offset from "View" of same document
				this.setLocation(30 * (docNo % n) + 15, 20 * (docNo % n) + 10);

				setVisible(true);
				jSplitPaneTop.setDividerLocation(0.30);
	    } catch(Exception e) {
	    	e.printStackTrace();
	    }
	}

	protected void jListValueChanged(ListSelectionEvent evt) {
		if (evt.getValueIsAdjusting ())
			return;
		JList theList = (JList) evt.getSource();
		// bleach all of the not selected entities
		for (int i = 0; i < entities.length; i++) {
			if (!theList.isSelectedIndex(i)) {
				bleachEntity ((Annotation) entities[i]);
			}
		}
		jTextPane.clearLines();
		// highlight all of the selected entities
		for (int i = 0; i < entities.length; i++) {
			if (theList.isSelectedIndex(i)) {
			highlightEntity ((Annotation) entities[i]);
			}
		}
	}

  /**
   * highlights the text associated with all mentions of <CODE>entity</CODE>,
   * and draws lines connecting the mentions.
   * @param entity   the entity Annotation
   */

  private  void highlightEntity (Annotation entity) {
    SimpleAttributeSet highlighted = new SimpleAttributeSet();
    StyleConstants.setBackground(highlighted, Color.pink);
    StyleConstants.setBold(highlighted, true);
    setEntityAttribute(entity, highlighted);
    Vector mentions = mentionsOfEntity (entity);
    Color color = Color.BLUE;
    if (entity.get("status") == "response") color = Color.RED;
    if (entity.get("status") == "key") color = Color.GREEN;
  	connectMentions(mentions, color);
  }

  /**
   * removes attributes from the text associated with all mentions of
   * <CODE>entity</CODE>.
   * @param entity   the entity Annotation
   */

  private void bleachEntity (Annotation entity) {
    SimpleAttributeSet bleached = new SimpleAttributeSet();
    setEntityAttribute(entity, bleached);
  }

  private String entityDescription (Annotation entity) {
  	String id = (String) entity.get("id");
  	String descriptor = (String) entity.get("descriptor");
  	String status = (String) entity.get("status");
  	String statusSuffix = "";
  	if (status == "key")
  		statusSuffix = "[K]";
  	else if (status == "response")
  		statusSuffix = "[R]";
  	if (id != null || descriptor != null)
  	  return id + ": " + descriptor + statusSuffix;
  	else
  	  return Resolve.normalizeName(document.text(entity)) + statusSuffix;
  }

  private Vector mentionsOfEntity (Annotation entity) {
  	return (Vector) entity.get("mentions");
  }

  private void setEntityAttribute(Annotation entity, SimpleAttributeSet atrSet) {
  	Vector mentions = mentionsOfEntity (entity);
  	if (mentions == null) {
  		System.out.println ("EntityView:  entity has no mentions:");
  		System.out.println ("            " + entity);
  		return;
  	}
  	setAnnotationAttribute(mentions, atrSet);
  }

	/**
	 *  draw lines (of color 'color' connecting all the mentions of an entity.
	 */

  private void connectMentions(Vector mentions, Color color) {
  	Point p;
  	Point lastPoint = null;
  	for (int i=0; i<mentions.size(); i++) {
  		Annotation mention = (Annotation) mentions.get(i);
  		int start = mention.span().start();
  		try {
  			Rectangle r = jTextPane.modelToView(start);
  			p = r.getLocation();
  		} catch (BadLocationException e) {
  			return;
  		}
  		if (i > 0)
  			jTextPane.addLine (lastPoint, p, color);
  		lastPoint = p;
  	}
  }

  /**
   *  stand-alone utility to display coreference relations, invoked by
   *  jet -CorefView document.  Passed an array with "-CorefView" and
   *  the file name of the document.  The file name may be optionally
   *  preceded by '-encoding' and the name of a character set.
   */

  public static void task (String[] args) {
  	String encoding = null;
  	String file = null;
  	if (args.length == 4 && args[1].equals("-encoding")) {
  		encoding = args[2];
  		file = args[3];
  	} else if (args.length == 2) {
  		file = args[1];
  	} else {
			System.out.println
			  ("CorefView requires 1 argument: jet -CorefView [-encoding e] <document>");
			System.exit(1);
		}
		// exit if invalid encoding is provided
		if (encoding != null) {
			if (!JetTest.setEncoding(encoding)) System.exit(1);
		}
		ExternalDocument doc = new ExternalDocument("sgml", file);
		doc.setAllTags(true);
		doc.open();
		CorefFilter.buildEntitiesFromMentions(doc);
		new EntityView (doc, 0);
	}

}
