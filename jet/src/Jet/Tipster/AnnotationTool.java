// -*- tab-width: 4 -*-
package Jet.Tipster;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.util.*;

import Jet.Zoner.SentenceSplitter;
import Jet.Lex.Tokenizer;
import Jet.Lisp.FeatureSet;
import Jet.JetTest;

/**
 *  a tool for manually adding annotations to a Document.
 *  To annotate a document,                                 <br>
 *  1) create an AnnotationTool                             <br>
 *  2) invoke method addType for each type to be annotated  <br>
 *  3) invoke method AnnotateDocument with the document     <p>
 *
 *  In addition to the keys for assigning specific types,
 *  specified by calls to addType, the tool recognized the
 *  following keystrokes:  <ul>
 *  <li>  right arrow:  move selection one token to right
 *  <li>  left arrow:  move selection one token to left
 *  <li>  shift - right arrow:  extend selection one token to right
 *  <li>  shift - left arrow:  extend selections one token to left
 *  <li>  u:  (unAnnotate) remove the annotation on the selected tokens
 *        (the selection must exactly correspond to the span of an annotation)
 *  <li>  q:  quit, return false (from annotateDocument method)
 *  <li>  Q:  quit, return true (from annotateDocument method)
 *  <li>  blank:  if document is scrolled, scroll to beginning of
 *        region to be annotated
 *  </ul>
 */

public class AnnotationTool extends JFrame {

	// the document being annotated
  private Document document;
  StyledDocument styledDocument, styledInstructionDocument;
  // the tokens within the annotationZone
  private Annotation[] tokens;
  // for each token, whether it is spanned by a newly-assigned annotation
  private boolean[] tagged;
  // for each token, the newly-assigned annotation starting at that token
  // (or null, if none)
  private Annotation[] newAnnotation;
  // the start and end point of the currently selected text
  private int dot, mark;
  // set true if user types 'Q'
  private boolean quit = false;
  private int start, length;

  private JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
  private JTextPane textPane = new JTextPane();
	private JTextPane instructionPane = new JTextPane();
  private JScrollPane scrollPane1 = new JScrollPane();
  private JScrollPane scrollPane2 = new JScrollPane();
  private TitledBorder border = new TitledBorder("quack");

  private Keymap keymap;
  private ArrayList actions= new ArrayList();
  private String instructions = "";

  /**
   * Creates a new AnnotationTool.
   */

  public AnnotationTool () {
    try {
      initTool();
      this.setTitle("AnnotationTool");
    }
    catch(Exception e) {
      e.printStackTrace();
    }
  }

  /**
   *  initialize the tool:  set up standard key bindings and assemble panes
   *  into frame.
   */

	private void initTool() {
		textPane.setEditable(false);
		keymap = JTextComponent.addKeymap("AnnotationToolMap", null);
		KeyStroke left = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0);
		keymap.addActionForKeyStroke(left, new SelectionAction("left"));
		KeyStroke right = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0);
		keymap.addActionForKeyStroke(right, new SelectionAction("right"));
  	KeyStroke shiftLeft = KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.SHIFT_MASK );
  	keymap.addActionForKeyStroke(shiftLeft, new SelectionAction("shiftLeft"));
  	KeyStroke shiftRight = KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.SHIFT_MASK );
  	keymap.addActionForKeyStroke(shiftRight, new SelectionAction("shiftRight"));
  	KeyStroke u = KeyStroke.getKeyStroke('u');
  	keymap.addActionForKeyStroke(u, new UndoAction());
  	KeyStroke q = KeyStroke.getKeyStroke('q');
  	keymap.addActionForKeyStroke(q, new QuitAction(this, false));
  	KeyStroke shiftQ = KeyStroke.getKeyStroke('Q');
  	keymap.addActionForKeyStroke(shiftQ, new QuitAction(this, true));
  	KeyStroke blank = KeyStroke.getKeyStroke(' ');
  	keymap.addActionForKeyStroke(blank, new ScrollAction());
  	textPane.setKeymap(keymap);
  	MouseListener cat = new AnnotationToolMouseListener();
  	textPane.addMouseListener(cat);
  	scrollPane1.getViewport().add(textPane);
    splitPane.setTopComponent(scrollPane1);
    scrollPane2.getViewport().add(instructionPane);
    splitPane.setBottomComponent(scrollPane2);
    splitPane.setResizeWeight(0.8);
    border.setTitlePosition(TitledBorder.BOTTOM);
    scrollPane1.setBorder (border);
    this.setSize(600, 400);
    this.getContentPane().add(splitPane, BorderLayout.CENTER);
    this.addWindowListener(new AnnotationToolWindowListener(this));
  }

  /**
   *  specifies that pressing key 'key' will cause an Annotation with
   *  type and features specified by 'annotationPrototype' to be added
   *  to the document over the selected text.  Note:  key 'u' is reserved
   *  for the unAnnotate operation and key 'q' is used to quit;  these
   *  keys should not be used to add a type of annotation.
   */

  public void addType (char key, Annotation annotationPrototype) {
  	KeyStroke stroke = KeyStroke.getKeyStroke(key);
  	AddAnnotationAction act = new AddAnnotationAction (key, annotationPrototype);
  	keymap.addActionForKeyStroke(stroke, act);
  	actions.add(act);
  	instructions += act.instruction + "\n";
  }

  /**
   *  display annotation tool with Document 'doc', allowing user to
   *  add annotations within Span 'annotationZone' of the document. Returns
   *  'false' if user exits with 'q', 'true' if user exits with 'Q'.
   */

  public synchronized boolean annotateDocument (Document doc, Span annotationZone) {
  	document = doc;
  	textPane.setText(document.text().toString());
  	styledDocument = textPane.getStyledDocument();
  	// set up and color instructions
  	instructionPane.setText(instructions);
  	styledInstructionDocument = instructionPane.getStyledDocument();
  	int posn = 0;
  	for (int i=0; i<actions.size(); i++) {
  		AddAnnotationAction act = (AddAnnotationAction) actions.get(i);
  		int length = act.instruction.length();
  		styledInstructionDocument.setCharacterAttributes(posn, length, act.colorAttribute, true);
  		posn += length + 1;
  	}
  	// boldface region to be annotated
  	SimpleAttributeSet boldface = new SimpleAttributeSet();
  	StyleConstants.setBold(boldface, true);
  	start = annotationZone.start();
    length =annotationZone.endNoWS(document) - start;
    styledDocument.setCharacterAttributes(start, length, boldface, true);
    // get tokens in region
    tokens = Tokenizer.gatherTokens(document, annotationZone);
    if (tokens.length == 0) {
    	System.out.println ("Can't annotate document:  not tokenized");
    	return false;
    }
    tagged = new boolean[tokens.length];
    newAnnotation = new Annotation[tokens.length];
    // highlight text for Annotations of interest in input document
    markPreAnnotations();
    // initialize selection
    dot = 0;
    mark = 0;
    // highlight selected tokens
    highlightSelection(true);
    // if external document, put name of document in window title
    if (doc instanceof ExternalDocument) {
    	ExternalDocument edoc = (ExternalDocument) doc;
    	String fileName = edoc.fileName();
    	setTitle("AnnotationTool:  " + fileName);
    }
    // display window
  	setVisible(true);
    // don't return until window closes
  	try {
  		wait();
  	} catch (InterruptedException ignored) {
  	}
  	return quit;
  }

  /**
   *  scan document for existing annotations matching those specified by
   *  calls on 'addType'.  Record and highlight such annotations (they are
   *  treated the same as annotations added by the tool).
   */

  private void markPreAnnotations () {
  	for (int itoken=0; itoken<tokens.length; itoken++) {
  		Annotation token = tokens[itoken];
  		int start = token.start();
  		int end = token.end();
  		Vector annotations = document.annotationsAt(start);
  		for (int i=0; i<annotations.size(); i++) {
  			Annotation ann = (Annotation) annotations.get(i);
  			for (int j=0; j<actions.size(); j++) {
  				AddAnnotationAction act = (AddAnnotationAction) actions.get(j);
  				if (ann.type().equals(act.annotationType) &&
  				    act.fs.subsetOf(ann.attributes())) {
			    	for (int k=itoken; tokens[k].span().end()<=end; k++)
			  			tagged[k] = true;
			  		newAnnotation[itoken] = ann;
			    	border.setTitle (ann.toString());
			    	scrollPane1.repaint();
			    	// color background to show annotation
			    	int length =ann.span().endNoWS(document) - start;
			    	styledDocument.setCharacterAttributes(start, length, act.colorAttribute, false);
  					break;
  				}
  			}
  		}
  	}
  }

  private void highlightSelection(boolean highlight) {
  	SimpleAttributeSet selection = new SimpleAttributeSet();
  	StyleConstants.setUnderline(selection, highlight);
  	int startToken = Math.min(dot, mark);
  	int endToken = Math.max(dot, mark);
  	int start = tokens[startToken].span().start();
    int length =tokens[endToken].span().endNoWS(document) - start;
    styledDocument.setCharacterAttributes(start, length, selection, false);
    Annotation ann = newAnnotation[startToken];
    if (highlight) {
    	border.setTitle(ann == null ? " " : ann.toString());
	    scrollPane1.repaint();
	  }
  }

  String[] addTypes (AnnotationColor ac) {
	ArrayList colors = ac.colors;
	HashSet typeSet = new HashSet();
	for (int i=0; i<colors.size(); i++) {
		AnnotationColorEntry entry = (AnnotationColorEntry) colors.get(i);
		Annotation ann = new Annotation (entry.type, null,
						 new FeatureSet(entry.feature,
								entry.featureValue));
		char key = entry.key;
		addType (key, ann);
		typeSet.add(entry.type);
	}
	String[] types = (String[]) typeSet.toArray(new String[0]);
	return types;
  }

  public static void main (String[] args) {
	if (args.length != 2) {
		System.err.println ("AnnotationTool requires two arguments:  document  colorFile");
		System.exit (1);
	}
	String docFile = args[0];
	String colorFile = args[1];
	AnnotationColor ac = new AnnotationColor(".", colorFile);
	AnnotationColor.showColors();
	AnnotationTool tool = new AnnotationTool();
	String[] types = tool.addTypes(ac);
	JetTest.encoding = "UTF-8";
	ExternalDocument doc = new ExternalDocument("sgml", docFile);
	doc.setSGMLtags(types);
	doc.open();
	doc.annotateWithTag("TEXT");
	Span textSpan;
	Vector textSegments = doc.annotationsOfType ("TEXT");
	if (textSegments != null && textSegments.size() > 0) {
		Annotation text = (Annotation) textSegments.get(0);
		textSpan = text.span();
	} else {
		textSpan = doc.fullSpan();
	}
	SentenceSplitter.split (doc, textSpan);
	Vector sentences = doc.annotationsOfType ("sentence");
	if (sentences == null) return;
	Iterator is = sentences.iterator ();
	while (is.hasNext ()) {
		Annotation sentence = (Annotation)is.next ();
		Span sentenceSpan = sentence.span();
		Tokenizer.tokenize (doc, sentenceSpan);
	}
	tool.annotateDocument (doc, textSpan);
	doc.removeAnnotationsOfType("token");
	doc.shrinkAll();
	// don't wrap on write
	doc.setSGMLwrapMargin(0);
	doc.save();
	System.exit(0);
  }

  /**
   *  a keyboard-triggered action which scrolls the text window to the
   *  text to be annotated.  This action is keyboard-triggered (rather than
   *  automatic) to avoid synchronization problems.
   */

  class ScrollAction extends AbstractAction {
  	ScrollAction () {
  	}
  	public void actionPerformed (ActionEvent e) {
  		try {
  			Rectangle r = textPane.modelToView(start + length);
  			textPane.scrollRectToVisible(r);
  		} catch (BadLocationException ee) {
  			System.out.println ("AnnotationTool:  invalid selected sentence");
  		}
  	}
  }

  /**
   *  the action of modifying the selection (dot and mark)
   */

  class SelectionAction extends AbstractAction {
  	String key;
  	SelectionAction (String key) {
  		this.key = key;
  	}
  	public void actionPerformed (ActionEvent e) {
  		highlightSelection(false);
  		if (key == "left") {
  			if (dot > 0)
  				dot--;
  			mark = dot;
  		} else if (key == "right") {
  			if (dot < tokens.length -1)
  				dot++;
  			mark = dot;
  		} else if (key == "shiftLeft") {
  			if (dot > 0) {
  				dot--;
  			}
  		} else if (key == "shiftRight") {
  			if (dot < tokens.length -1) {
  				dot++;
  			}
  		}
  		highlightSelection(true);
  	}
  }

  /**
   *  the action of removing an annotation
   */

  class UndoAction extends AbstractAction {
  	public void actionPerformed (ActionEvent e) {
  		// get selected tokens
  		int startToken = Math.min(dot, mark);
  		int endToken = Math.max(dot, mark);
  		int start = tokens[startToken].span().start();
  		int end = tokens[endToken].span().end();
  		// verify that selected region corresponds to a new annotation
  		Annotation ann = newAnnotation[startToken];
  		if (ann == null ||
  		    ann.span().end() != end) {
	    	JOptionPane.showMessageDialog(null,"Can't undo:\n"
			                              + "selection doesn't correspond to annotation",
			                              "Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
	  	// erase annotation
	  	newAnnotation[startToken] = null;
	  	for (int i=startToken; i<=endToken; i++)
	  		tagged[i] = false;
	  	document.removeAnnotation(ann);
	  	// bleach background
    	int length =tokens[endToken].span().endNoWS(document) - start;
    	SimpleAttributeSet colorAttribute = new SimpleAttributeSet();
  		StyleConstants.setBackground(colorAttribute, Color.white);
    	styledDocument.setCharacterAttributes(start, length, colorAttribute, false);
    	border.setTitle(" ");
    	scrollPane1.repaint();
	  }
	}

	/*
	 *  action initiated by 'q' or 'Q' key:  delete window and return from
	 *  annotateDocument.
	 */

	class QuitAction extends AbstractAction {
		AnnotationTool tool;
		boolean stopLearner;
  	public QuitAction (AnnotationTool t, boolean stop) {
  		tool = t;
  		stopLearner = stop;
  	}
  	public void actionPerformed (ActionEvent e) {
  		dispose();
  		if (stopLearner)
  			quit = true;
  		synchronized (tool) {
      	tool.notify();
      }
    }
  }

  /**
   *  the action of adding an annotation to the document.
   */

  class AddAnnotationAction extends AbstractAction {
  	char keychar;
  	String annotationType;
  	FeatureSet fs;
  	Color color;
  	SimpleAttributeSet colorAttribute;
  	String instruction;

  	AddAnnotationAction (char c, Annotation ann) {
  		keychar = c;
  		annotationType = ann.type();
  		fs = ann.attributes();
  		Color color = AnnotationColor.getColor(ann);
  		colorAttribute = new SimpleAttributeSet();
  		StyleConstants.setBackground(colorAttribute, color);
  		instruction = keychar + ": " + annotationType;
  		if (fs != null) instruction += " " + fs;
  	}

  	public void actionPerformed (ActionEvent e) {
  		// get selected tokens
  		int startToken = Math.min(dot, mark);
  		int endToken = Math.max(dot, mark);
  		// check that they aren't already annotated
  		for (int i=startToken; i<=endToken; i++) {
  			if (tagged[i]) {
  				JOptionPane.showMessageDialog(null,"Can't add annotation:\n"
  				                              + "selection already has annotation",
  				                              "Error", JOptionPane.ERROR_MESSAGE);
  				return;
  			}
  		}
  		// add annotation to document
  		for (int i=startToken; i<=endToken; i++)
  			tagged[i] = true;
  		int start = tokens[startToken].span().start();
    	int end =tokens[endToken].span().end();
    	Annotation newAnn = new Annotation(annotationType, new Span(start, end), fs);
    	document.addAnnotation(newAnn);
    	newAnnotation[startToken] = newAnn;
    	border.setTitle (newAnn.toString());
    	scrollPane1.repaint();
    	// color background to show annotation
    	int length =tokens[endToken].span().endNoWS(document) - start;
    	styledDocument.setCharacterAttributes(start, length, colorAttribute, false);
    }
  }

  /*
   *  extends WindowAdapter to dispose of the window on closing, and to
   *  return from the call on annotateDocument which displayed the window.
   */

  class AnnotationToolWindowListener extends WindowAdapter {
  	AnnotationTool tool;
  	public AnnotationToolWindowListener (AnnotationTool t) {
  		tool = t;
  	}
  	public void windowClosing(WindowEvent evt) {
      dispose();
      synchronized (tool) {
      	tool.notify();
      }
    }
  }

  /**
   *  implements a MouseListener which allows the user to select tokens
   *  by dragging the mouse over the text.  'dot' is set to the point where
   *  the mouse is depressed, 'mark' to the point where it is released.
   *  In both cases, we select the token which includes the current mouse
   *  position.
   */

  class AnnotationToolMouseListener implements MouseListener {

  	public void mousePressed (MouseEvent event) {
  		Point point = event.getPoint();
  		int charPos = textPane.viewToModel(point);
  		int token = findToken(charPos);
  		if (token >= 0) {
  			highlightSelection(false);
  			dot = token;
  			mark = token;
  			highlightSelection(true);
  		}
  	}

  	public void mouseReleased (MouseEvent event) {Point point = event.getPoint();
  		int charPos = textPane.viewToModel(point);
  		int token = findToken(charPos);
  		if (token >= 0) {
  			highlightSelection(false);
  			mark = token;
  			highlightSelection(true);
  		}
  	}

  	public void mouseClicked (MouseEvent event) {}
  	public void mouseEntered (MouseEvent event) {}
  	public void mouseExited (MouseEvent event) {}

  }

  /**
   *  given a character position 'charPos' in the document, returns the index
   *  of the token which contains that character position;  if the position is
   *  not contained in any (annotatable) token, returns -1.
   */

  int findToken (int charPos) {
  	if (charPos < 0)
  		return charPos;
  	for (int i=0; i<tokens.length; i++) {
  		if (charPos >= tokens[i].start() && charPos < tokens[i].end())
  			return i;
  	}
  	return -1;
  }

}
