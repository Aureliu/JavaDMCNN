// -*- tab-width: 4 -*-
package Jet.Tipster;

import Jet.Parser.ParseView;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;
import java.util.*;

/**
 *  displays a <CODE>Document</CODE> with its annotations.
 *  <p>
 *  The top left pane lists all the annotation types in the Document.  The top
 *  right pane shows the Document text.  Selecting an annotation type highlights
 *  all the instances of annotations of that type in the Document.  Selecting
 *  text in the Document pane lists, on the lower pane, all annotations which
 *  begin within the selected region.
 *  <p>
 *  In addition, if the selected region contains a 'sentence' annotation with
 *  a 'parse' attribute pointing to a sentence parse, typing 'p' causes that
 *  parse to be displayed graphically using the 'ParseView' class.
 *
 *  @author dig
 */
public class View extends JFrame {

  protected Document document;

  protected JSplitPane jSplitPaneTop = new JSplitPane();
  private JSplitPane jSplitPaneMain = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
  protected JTextPaneX jTextPane = new JTextPaneX();
  private JTextArea jTextArea = new JTextArea();
  private JScrollPane jScrollPaneLeft = new JScrollPane();
  private JScrollPane jScrollPaneRight = new JScrollPane();
  private JScrollPane jScrollPaneBottom = new JScrollPane();
  private JPanel jPanelLeft = new JPanel();
  private JPanel jPanelRight = new JPanel();
  private JPanel jPanelBottom = new JPanel();
  private Annotation parse = null;


  /**
   * Creates a new View.
   * @param doc document to display
   * @param docNo number of document (used on window title)
   */

  public View(Document doc, int docNo) {
    document = doc;
    try {
      jbInit(document.getAnnotationTypes(), "types");
      this.setTitle("Jet Document " + docNo);

      // Cascades windows
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      Dimension frameSize = this.getSize();
      int n = (screenSize.width - frameSize.width) / 30;
      this.setLocation(30 * (docNo % n), 20 * (docNo % n));

      setVisible(true);
    }
    catch(Exception e) {
      e.printStackTrace();
    }
  }

  // the following is needed for the subclass(EntityView)
  protected View() {
  }

  protected void jbInit(Object[] list, String listTitle) throws Exception {
    // Top Left
	JList jList = new JList(list);
    jList.setBorder(BorderFactory.createEmptyBorder(0,4,0,4));
    jList.addListSelectionListener(new ListSelectionListener () {
      public void valueChanged(ListSelectionEvent evt) {
        jListValueChanged(evt);
      }
    });
    jScrollPaneLeft.getViewport().add(jList);
    jPanelLeft.setBorder(new TitledBorder(
      BorderFactory.createEtchedBorder(
        Color.white, new Color(142, 142, 142)), listTitle));
    jPanelLeft.setLayout(new BorderLayout());
    jPanelLeft.add(jScrollPaneLeft, BorderLayout.CENTER);
    // Top Right
    jTextPane.setBorder(BorderFactory.createEmptyBorder(0,4,0,4));
    jTextPane.setEditable(false);
    jTextPane.setFont(new Font("dialog", Font.PLAIN, 12));
    jTextPane.setText(document.text().toString());
    jTextPane.addCaretListener(new CaretListener() {
      public void caretUpdate(CaretEvent evt) {
        jTextPaneCaretUpdate(evt);
      }
    });
    // enable p key ==> display parse
    Keymap keymap = JTextComponent.addKeymap("AnnotationToolMap", null);
    KeyStroke p = KeyStroke.getKeyStroke('p');
  	keymap.addActionForKeyStroke(p, new AbstractAction() {
  		public void actionPerformed (ActionEvent e) {
  			displayParse();
  		}
  	});
  	jTextPane.setKeymap(keymap);
    //
    jScrollPaneRight.getViewport().add(jTextPane);
    jPanelRight.setBorder(new TitledBorder(
      BorderFactory.createEtchedBorder(
        Color.white, new Color(142, 142, 142)), "Annotated Document"));
    jPanelRight.setLayout(new BorderLayout());
    jPanelRight.add(jScrollPaneRight, BorderLayout.CENTER);

    // Top
    jSplitPaneTop.setLeftComponent(jPanelLeft);
    jSplitPaneTop.setRightComponent(jPanelRight);

    // Bottom
    jTextArea.setBorder(BorderFactory.createEmptyBorder(0,4,0,4));
    jTextArea.setEditable(false);
    jTextArea.setText("Select a region to see annotations.");
    jScrollPaneBottom.getViewport().add(jTextArea);
    jPanelBottom.setPreferredSize(new Dimension(600, 80));
    jPanelBottom.setBorder(new TitledBorder(
      BorderFactory.createEtchedBorder(
        Color.white, new Color(142, 142, 142)), "Annotations"));
    jPanelBottom.setLayout(new BorderLayout());
    jPanelBottom.add(jScrollPaneBottom, BorderLayout.CENTER);

    // Frame
    jSplitPaneMain.setTopComponent(jSplitPaneTop);
    jSplitPaneMain.setBottomComponent(jPanelBottom);
    jSplitPaneMain.setResizeWeight(0.8);
    this.setSize(600, 400);
    this.getContentPane().add(jSplitPaneMain, BorderLayout.CENTER);
    this.addWindowListener(new WindowAdapter () {
      public void windowClosing(WindowEvent evt) {
        dispose();
      }
    });
  }

  /**
   *  responds to changes in text selected in the text pane by
   *  locating all annotations within the selected region and
   *  writing them into the annotation text buffer (bottom pane).
   */

  private void jTextPaneCaretUpdate(CaretEvent evt) {
    StringBuffer annBuffer = new StringBuffer();
    // get location in the document
    int dot = evt.getDot();
    int mark = evt.getMark();
    parse = null;
    for (int pos = Math.min(dot, mark); pos < Math.max(dot, mark); pos++) {
      Vector annotationsAtCaret = document.annotationsAt(pos);
      if (annotationsAtCaret != null) {
        Iterator it = annotationsAtCaret.iterator();
        while (it.hasNext()) {
          Annotation ann = (Annotation) it.next();
          annBuffer.append(ann.toString() + "\n");
          if (parse == null && ann.get("parse") != null) {
          	parse = (Annotation) ann.get("parse");
          	annBuffer.append("[type 'p' in document window to display parse]\n");
          }
        }
      }
    }
    String annText = annBuffer.toString();
    if (annText.length() == 0)
      annText = "No annotations found in the selected region.";
    jTextArea.setText(annText);
  }

  private void displayParse () {
  	if (parse != null)
  		new ParseView ("parse", parse);
  }

  /**
   *  respond to selections in the list of annotation types by highlighting
   *  all annotations of that type.
   */

  protected void jListValueChanged(ListSelectionEvent evt) {
    if (evt.getValueIsAdjusting ())
      return;
    JList theList = (JList) evt.getSource();
    // bleach all of the not selected types
    Object[] types = document.getAnnotationTypes();
    for (int i = 0; i < types.length; i++) {
      if (!theList.isSelectedIndex(i))
        bleach ((String) types[i]);
    }
    // highlight all of the selected types
    types = theList.getSelectedValues();
    for (int i = 0; i < types.length; i++) {
      highlight ((String) types[i]);
    }
  }

  /**
   * highlights all the text associated with annotations of type <CODE>type</CODE>.
   * @param type type of annotation to highlight in the text
   */
  public void highlight(String type) {
    AnnotationColor.addType(type);
    setAnnotationAttribute(type, null);
  }

  /**
   * removes attributes from all the text associated with annotations of type <CODE>type</CODE>.
   * @param type type of annotations to remove attributes from
   */
  public void bleach(String type) {
  	SimpleAttributeSet bleached = new SimpleAttributeSet();
    setAnnotationAttribute(type, bleached);
  }

  /**
   * sets the character attributes for the portions of a document associated
   * with annotations of type <CODE>annType</CODE>.
   * @param annType type of annotation to assign attribute to
   * @param atrSet attribute set to associate with text;  if null,
   *               use color associated with annotation type/feature
   */

  public void setAnnotationAttribute(String annType, SimpleAttributeSet atrSet) {
  	setAnnotationAttribute (document.annotationsOfType(annType), atrSet);
  }

  /**
   * sets the character attributes for the portions of a document associated
   * with <CODE>annotations</CODE>.
   * @param annotations annotations to assign attribute to
   * @param atrSet attribute set to associate with text;  if null,
   *               use color associated with annotation type/feature
   */

  public void setAnnotationAttribute(Vector annotations, SimpleAttributeSet atrSet) {
  	 SimpleAttributeSet highlighting;
     if (annotations != null && !annotations.isEmpty()) {
      StyledDocument styleDocument = jTextPane.getStyledDocument();
      Iterator it = annotations.iterator();
      while (it.hasNext()) {
        Annotation ann = (Annotation) it.next();
        if (atrSet != null) {
        	highlighting = atrSet;
        } else {
	        highlighting = new SimpleAttributeSet();
		    	Color color = AnnotationColor.getColor(ann);
		    	StyleConstants.setBackground(highlighting, color);
		    	StyleConstants.setBold(highlighting, true);
		    }
        int start = ann.span().start();
        int length =ann.span().endNoWS(document) - start;
        styleDocument.setCharacterAttributes(start, length, highlighting, true);
      }
    }
  }

  /**
   *  an extension of JTextPane which supports the drawing of lines
   *  on top of the text (a facility used by EntityView).
   */

  protected class JTextPaneX extends JTextPane {

  	private ArrayList startPoint = new ArrayList();
  	private ArrayList endPoint = new ArrayList();
  	private ArrayList color = new ArrayList();

  	/**
  	 *  erase all the lines in the TextPane
  	 */

  	public void clearLines () {
  		startPoint = new ArrayList();
  		endPoint = new ArrayList();
  		color = new ArrayList();
  	}

  	/**
  	 *  add a line from 'start' to 'end', color BLUE.
  	 */

  	public void addLine (Point start, Point end) {
  		addLine (start, end, Color.BLUE);
  	}

  	/**
  	 *  add a line from 'start' to 'end', color 'clr'.
  	 */

  	public void addLine (Point start, Point end, Color clr) {
  		startPoint.add(start);
  		endPoint.add(end);
  		color.add(clr);
  	}

  	/**
  	 *  paint the TextPane plus the lines specified by
  	 *  prior calls to 'addLine'.
  	 */

  	public void paintComponent (Graphics g) {
  		super.paintComponent(g);
  		for (int i=0; i<startPoint.size(); i++) {
  			Point start = (Point) startPoint.get(i);
  			Point end = (Point) endPoint.get(i);
  			Color clr = (Color) color.get(i);
  			g.setColor(clr);
  			// RED lines are offset by two pixels so that one can
  			// display both red and green lines (each two pixels wide)
  			// between the same pair of points.
  			int offset = (clr == Color.RED) ? 2 : 0;
  			g.drawLine(start.x+offset, start.y, end.x+offset, end.y);
  			g.drawLine(start.x+1+offset, start.y, end.x+1+offset, end.y);
  		}
  	}
  }
}
