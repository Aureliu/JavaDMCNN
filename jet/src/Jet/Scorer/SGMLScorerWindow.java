// -*- tab-width: 4 -*-
//Title:        JET
//Version:      1.03
//Description:  A Java-based Information Extraction Tool

package Jet.Scorer;

import Jet.Tipster.*;
import java.io.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.border.*;
import javax.swing.text.*;

public class SGMLScorerWindow extends JFrame implements ActionListener {

  JButton jButtonScore = new JButton();
  JButton jButtonReset = new JButton();
  JLabel jLabel = new JLabel();
  JTextField jTextField = new JTextField();
  JPanel jPanelBottom = new JPanel();
  JPanel jPanelBottomLeft = new JPanel();
  JPanel jPanelBottomRight = new JPanel();
  JPanel jPanelTopLeft = new JPanel();
  JPanel jPanelTopRight = new JPanel();
  JPanel jPanelButtons = new JPanel();
  JPanel jPanelTypeText = new JPanel();
  JSplitPane jSplitPane = new JSplitPane();
  JSplitPane jSplitPaneMain = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
  JScrollPane jScrollPaneLeft = new JScrollPane();
  JScrollPane jScrollPaneRight = new JScrollPane();
  JScrollPane jScrollPaneBottom = new JScrollPane();
  JTextPane jTextPaneLeft = new JTextPane();
  JTextPane jTextPaneRight = new JTextPane();
  JTextArea jTextAreaBottom = new JTextArea();
  final JFileChooser fc = new JFileChooser(".");
  SGMLScorer scorer = null;
  String loadedText1 = "";
  String loadedText2 = "";
  String text1 = null;
  String text2 = null;
  Jet.Tipster.Document doc1 = null;
  Jet.Tipster.Document doc2 = null;
  boolean keyProcessed = false;
  boolean responseProcessed = false;
  boolean editMode = false;

  public SGMLScorerWindow() {
    try {
//      this.scorer = scorer;

      jbInit();
      pack();

      //Center the window
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      Dimension frameSize = this.getSize();
      if (frameSize.height > screenSize.height) {
        frameSize.height = screenSize.height;
      }
      if (frameSize.width > screenSize.width) {
        frameSize.width = screenSize.width;
      }
      this.setLocation((screenSize.width - frameSize.width) / 2, (screenSize.height - frameSize.height) / 2);

      setVisible(true);
    }
    catch(Exception e) {
      e.printStackTrace();
    }
  }

  private void jbInit() throws Exception {
    this.setTitle("SGML Scorer");

    // Menu Bar
    JMenuBar jmb = new JMenuBar();
    JMenu fileMenu = new JMenu("File");
    fileMenu.setMnemonic(KeyEvent.VK_F);
    JMenuItem item;

    item = new JMenuItem("Load Key File ...");
    item.setMnemonic(KeyEvent.VK_K);
    fileMenu.add(item);
    item.addActionListener(this);

    item = new JMenuItem("Load Response File ...");
    item.setMnemonic(KeyEvent.VK_P);
    fileMenu.add(item);
    item.addActionListener(this);

    fileMenu.addSeparator();

    item = new JMenuItem("Exit");
    item.setMnemonic(KeyEvent.VK_X);
    fileMenu.add(item);
    item.addActionListener(this);

    JMenu modeMenu = new JMenu("Mode");
    modeMenu.setMnemonic(KeyEvent.VK_M);
    ButtonGroup group = new ButtonGroup();
    JRadioButtonMenuItem rbmItem;

    rbmItem = new JRadioButtonMenuItem("Edit Mode");
    rbmItem.setMnemonic(KeyEvent.VK_E);
    modeMenu.add(rbmItem);
    group.add(rbmItem);
    rbmItem.addActionListener(this);

    rbmItem = new JRadioButtonMenuItem("Non-Edit Mode");
    rbmItem.setSelected(true);
    rbmItem.setMnemonic(KeyEvent.VK_N);
    modeMenu.add(rbmItem);
    group.add(rbmItem);
    rbmItem.addActionListener(this);

    jmb.add(fileMenu);
    jmb.add(modeMenu);
    setJMenuBar(jmb);

    // Top Left
//    jTextPaneLeft.setMinimumSize(new Dimension(1600, 1200));
//    jTextPaneLeft.setPreferredSize(new Dimension(1600, 1200));
//    jTextPaneLeft.setSize(new Dimension(1600, 1200));
    jTextPaneLeft.setBorder(BorderFactory.createEmptyBorder(0,4,0,4));
    jTextPaneLeft.setFont(new Font("dialog", Font.PLAIN, 12));
    jTextPaneLeft.setEditable(false);
    jTextPaneLeft.addCaretListener(new CaretListener() {
      public void caretUpdate(CaretEvent evt) {
        jTextPaneLeftCaretUpdate(evt);
      }
    });
//    jScrollPaneLeft.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    jScrollPaneLeft.getViewport().add(jTextPaneLeft, null);
    jPanelTopLeft.setBorder(BorderFactory.createCompoundBorder(
                              new TitledBorder(BorderFactory.createEtchedBorder(
                                Color.white, new Color(142, 142, 142)), "Key File"),
                              BorderFactory.createEmptyBorder(4,8,4,4)));
    jPanelTopLeft.setLayout(new BorderLayout());
    jPanelTopLeft.add(jScrollPaneLeft, BorderLayout.CENTER);

    // Top Right
    jTextPaneRight.setBorder(BorderFactory.createEmptyBorder(0,4,0,4));
    jTextPaneRight.setFont(new Font("dialog", Font.PLAIN, 12));
    jTextPaneRight.setEditable(false);
    jTextPaneRight.addCaretListener(new CaretListener() {
      public void caretUpdate(CaretEvent evt) {
        jTextPaneRightCaretUpdate(evt);
      }
    });
//    jScrollPaneRight.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
    jScrollPaneRight.getViewport().add(jTextPaneRight, null);
    jPanelTopRight.setBorder(BorderFactory.createCompoundBorder(
                               new TitledBorder(BorderFactory.createEtchedBorder(
                                 Color.white, new Color(142, 142, 142)), "Response File"),
                               BorderFactory.createEmptyBorder(4,8,4,4)));
    jPanelTopRight.setLayout(new BorderLayout());
    jPanelTopRight.add(jScrollPaneRight, BorderLayout.CENTER);

    // Top
    jSplitPane.setPreferredSize(new Dimension(1016, 510));
//    jSplitPane.setResizeWeight(0.5);
    jSplitPane.setDividerLocation(500);
    jSplitPane.add(jPanelTopLeft, JSplitPane.LEFT);
    jSplitPane.add(jPanelTopRight, JSplitPane.RIGHT);

    // Bottom Left
    jTextField.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        scorer();
      }
    });
    jButtonScore.setText("Score");
    jButtonScore.setToolTipText("Score homology of the two files");
    jButtonScore.setMnemonic(KeyEvent.VK_S);
    jButtonScore.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        scorer();
      }
    });

    jButtonReset.setText("Reset");
    jButtonReset.setToolTipText("Reset texts to original form");
    jButtonReset.setMnemonic(KeyEvent.VK_R);
    jButtonReset.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        jTextPaneLeft.setText(loadedText1);
        jTextPaneRight.setText(loadedText2);
        jTextAreaBottom.setText("");
        jTextField.selectAll();
        doc1 = null;
        doc2 = null;
        keyProcessed = false;
        responseProcessed = false;
      }
    });

    jPanelButtons.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
    jPanelButtons.setLayout(new GridLayout(1, 2));
    jPanelButtons.add(jButtonScore);
    jPanelButtons.add(jButtonReset);

    jLabel.setText("Type(s):  ");
    jLabel.setToolTipText("The type(s) to be scored, separated by ','  Case sensitive");

    jPanelTypeText.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
    jPanelTypeText.setLayout(new BorderLayout());
    jPanelTypeText.add(jLabel, BorderLayout.WEST);
    jPanelTypeText.add(jTextField, BorderLayout.CENTER);

    jPanelBottomLeft.setBorder(BorderFactory.createEmptyBorder(4,4,4,4));
    jPanelBottomLeft.setLayout(new GridLayout(2, 1));
    jPanelBottomLeft.add(jPanelTypeText);
    jPanelBottomLeft.add(jPanelButtons);

    // Bottom Right
    jTextAreaBottom.setBorder(BorderFactory.createEmptyBorder(0,4,0,4));
    jTextAreaBottom.setEditable(false);
    jScrollPaneBottom.getViewport().add(jTextAreaBottom, null);
    jPanelBottomRight.setBorder(BorderFactory.createCompoundBorder(
                              new TitledBorder(BorderFactory.createEtchedBorder(
                                Color.white, new Color(142, 142, 142)), "Statistics"),
                              BorderFactory.createEmptyBorder(4,8,4,4)));
    jPanelBottomRight.setLayout(new BorderLayout());
    jPanelBottomRight.add(jScrollPaneBottom, BorderLayout.CENTER);

    // Bottom
    jPanelBottom.setBorder(BorderFactory.createEmptyBorder(0,5,0,5));
    jPanelBottom.setLayout(new BorderLayout());
    jPanelBottom.add(jPanelBottomLeft, BorderLayout.WEST);
    jPanelBottom.add(jPanelBottomRight, BorderLayout.CENTER);
    jPanelBottom.setPreferredSize(new Dimension(1016, 70));

    // Main split pane
    jSplitPaneMain.setBorder(BorderFactory.createEmptyBorder(1,0,0,0));
    jSplitPaneMain.add(jSplitPane, JSplitPane.TOP);
    jSplitPaneMain.add(jPanelBottom, JSplitPane.BOTTOM);
//    jSplitPaneMain.setResizeWeight(1);

    // JFrame
    this.addWindowListener(new WindowAdapter() {
      public void windowClosing() {
        SGMLScorerWindow.this.dispose();
      }
    });
    this.getContentPane().setLayout(new BorderLayout());
    this.getContentPane().add(jSplitPaneMain, BorderLayout.CENTER);
  }

 /**
   *  called to handle menu actions.
   */
  public void actionPerformed(ActionEvent e) {
    if (e.getActionCommand().equals("Load Key File ...")) {
      fc.setDialogTitle("Load Key File");
      fc.setApproveButtonText("Load");
      fc.setApproveButtonMnemonic(KeyEvent.VK_L);
      fc.setApproveButtonToolTipText("Load key file");
      int returnVal = fc.showDialog(SGMLScorerWindow.this, null);
      if (returnVal == JFileChooser.APPROVE_OPTION) {
        try {
          File file = fc.getSelectedFile();
          BufferedReader reader = new BufferedReader(new FileReader(file));
          String line = null;
          StringBuffer keyFileText = new StringBuffer();
          while((line = reader.readLine()) != null)
            keyFileText.append(line + "\n");
          loadedText1 = keyFileText.toString();
          jTextPaneLeft.setText(loadedText1);
          keyProcessed = false;
          doc1 = null;
        }
        catch (IOException ex) {
          ex.printStackTrace();
        }
      }
    }
    else if (e.getActionCommand().equals("Load Response File ...")) {
      fc.setDialogTitle("Load Response File");
      fc.setApproveButtonText("Load");
      fc.setApproveButtonMnemonic(KeyEvent.VK_L);
      fc.setApproveButtonToolTipText("Load response file");
      int returnVal = fc.showDialog(SGMLScorerWindow.this, null);
      if (returnVal == JFileChooser.APPROVE_OPTION) {
        try {
          File file = fc.getSelectedFile();
          BufferedReader reader = new BufferedReader(new FileReader(file));
          String line = null;
          StringBuffer responseFileText = new StringBuffer();
          while((line = reader.readLine()) != null)
            responseFileText.append(line + "\n");
          loadedText2 = responseFileText.toString();
          jTextPaneRight.setText(loadedText2);
          responseProcessed = false;
          doc2 = null;
        }
        catch (IOException ex) {
          ex.printStackTrace();
        }
      }
    }
    else if (e.getActionCommand().equals("Exit")) {
      this.dispose();
    }
    else if (e.getActionCommand().equals("Edit Mode")) {
      editMode = true;
      jTextPaneLeft.setEditable(true);
      jTextPaneRight.setEditable(true);
    }
    else { // e.getActionCommand().equals("Non-Edit Mode")
      editMode = false;
      jTextPaneLeft.setEditable(false);
      jTextPaneRight.setEditable(false);
    }
  }

  private void scorer() {
    String[] types = getTypes();
    if (editMode) {
          text1 = jTextPaneLeft.getText();
          text2 = jTextPaneRight.getText();
          doc1 = SGMLProcessor.sgmlToDoc(text1, types);
          doc2 = SGMLProcessor.sgmlToDoc(text2, types);
        }
        else {
          if (!keyProcessed) {
            doc1 = SGMLProcessor.sgmlToDoc(loadedText1, types);
            keyProcessed = true;
          }
          if (!responseProcessed) {
            doc2 = SGMLProcessor.sgmlToDoc(loadedText2, types);
            responseProcessed = true;
          }
        }

        jTextPaneLeft.setText(doc1.text());
        jTextPaneRight.setText(doc2.text());

        scorer = new SGMLScorer(doc1, doc2);

        for (int i = 0; i < types.length; i++)
          scorer.match(types[i]);

        for (int i = 0; i < types.length; i++)
          highlight(types[i]);

        float precision = (scorer.totalTagsInDoc2 == 0)?
                          -100 : 10000 * scorer.numOfMatchingTags / (scorer.totalTagsInDoc2);
        precision /= 100.0;
        float recall = (scorer.totalTagsInDoc1 == 0)?
                          -100 : 10000 * scorer.numOfMatchingTags / (scorer.totalTagsInDoc1);
        recall /= 100.0;
        float typeAccuracy = (scorer.numOfMatchingTags == 0)? -100
                             : 10000 * scorer.numOfMatchingAttrs / scorer.numOfMatchingTags;
        typeAccuracy /= 100.0;

        StringBuffer statistics = new StringBuffer();
        statistics.append("# of matching tags = " + scorer.totalMatchingTags);
        statistics.append(";  # of tags in key = " + scorer.totalTagsInDoc1);
        statistics.append(";  # of tags in response = " + scorer.totalTagsInDoc2);
        statistics.append(";\nprecision = " + ((precision < 0)? "NA" : (precision + "%")));
        statistics.append(";  recall = " + ((recall < 0)? "NA" : (recall + "%")));
        statistics.append(";  type accuracy = " + ((typeAccuracy < 0)? "NA" : (typeAccuracy + "%")));
        jTextAreaBottom.setText(statistics.toString());

        // highlight background of unmatching lines
        highlightMismatches();
        jTextField.selectAll();
  }

  private String[] getTypes() {
    Vector types = new Vector();
    String jTextFieldContent = jTextField.getText();
    StringTokenizer stok = new StringTokenizer(jTextFieldContent, ", \t\n\r\f");
    while (stok.hasMoreTokens())
      types.add(stok.nextToken());
    Object[] tmp = types.toArray();
    String[] returnTypes = new String[tmp.length];
    for (int i = 0; i < tmp.length; i++)
      returnTypes[i] = (String) tmp[i];
    return returnTypes;
  }

  private void highlight(String type) {
    SimpleAttributeSet highlighted = new SimpleAttributeSet();
    Color color = new Color(type.hashCode());
    StyleConstants.setForeground(highlighted, color);
    StyleConstants.setBold(highlighted, true);
    setAnnotationAttribute(type, highlighted);
  }

  private void bleach(String type) {
    SimpleAttributeSet bleached = new SimpleAttributeSet();
    setAnnotationAttribute(type, bleached);
  }

  private void setAnnotationAttribute(String annType, AttributeSet attrSet) {
    Vector annotationsOfType1 = doc1.annotationsOfType(annType);
    if (annotationsOfType1 != null) {
      StyledDocument sd = jTextPaneLeft.getStyledDocument();
      Iterator i = annotationsOfType1.iterator();
      while (i.hasNext()) {
        Annotation ann = (Annotation) i.next();
        int start = ann.span().start();
        int length = ann.span().endNoWS(doc1) - start;
        sd.setCharacterAttributes(start, length, attrSet, true);
      }
    }

    Vector annotationsOfType2 = doc2.annotationsOfType(annType);
    if (annotationsOfType2 != null) {
      StyledDocument sd = jTextPaneRight.getStyledDocument();
      Iterator i = annotationsOfType2.iterator();
      while (i.hasNext()) {
        Annotation ann = (Annotation) i.next();
        int start = ann.span().start();
        int length = ann.span().endNoWS(doc2) - start;
        sd.setCharacterAttributes(start, length, attrSet, true);
      }
    }
  }

  private void highlightMismatches() {
    SimpleAttributeSet unmatchLine = new SimpleAttributeSet();
    StyleConstants.setBackground(unmatchLine, Color.orange);

    if (scorer.mismatch1 != null) {
      StyledDocument sd = jTextPaneLeft.getStyledDocument();
      Iterator i = scorer.mismatch1.iterator();
      while (i.hasNext()) {
        Annotation ann = (Annotation) i.next();
        int start = ann.span().start();
        int length = ann.span().endNoWS(doc1) - start;
        sd.setCharacterAttributes(start, length, unmatchLine, false);
      }
    }
    if (scorer.mismatch2 != null) {
      StyledDocument sd = jTextPaneRight.getStyledDocument();
      Iterator i = scorer.mismatch2.iterator();
      while (i.hasNext()) {
        Annotation ann = (Annotation) i.next();
        int start = ann.span().start();
        int length = ann.span().endNoWS(doc2) - start;
        sd.setCharacterAttributes(start, length, unmatchLine, false);
      }
    }
  }

  private void jTextPaneLeftCaretUpdate(CaretEvent evt) {
    String toolTip = null;
    StringBuffer annBuffer = new StringBuffer();
    int dot = evt.getDot();
    int mark = evt.getMark();
//    int pos = Math.min(dot, mark);
    if (doc1 == null) return;
    for (int pos = Math.min(dot,mark);
         pos < Math.max(dot,mark) || (pos == dot && dot == mark);
         pos++) {
      Vector annotationsAtCaret = doc1.annotationsAt(pos);
      if (annotationsAtCaret != null) {
        Iterator i = annotationsAtCaret.iterator();
        while (i.hasNext()) {
          Annotation ann = (Annotation) i.next();
          annBuffer.append(ann.toString() + " ");
        }
      }
    }
    toolTip = annBuffer.toString();
    if (toolTip.length() != 0)
      jTextPaneLeft.setToolTipText(toolTip);
    else
      jTextPaneLeft.setToolTipText(null);
  }

  private void jTextPaneRightCaretUpdate(CaretEvent evt) {
    String toolTip = null;
    StringBuffer annBuffer = new StringBuffer();
    int dot = evt.getDot();
    int mark = evt.getMark();
//    int pos = Math.min(dot, mark);
    if (doc2 == null) return;
    for (int pos = Math.min(dot,mark);
         pos < Math.max(dot,mark) || (pos == dot && dot == mark);
         pos++) {
      Vector annotationsAtCaret = doc2.annotationsAt(pos);
      if (annotationsAtCaret != null) {
        Iterator i = annotationsAtCaret.iterator();
        while (i.hasNext()) {
          Annotation ann = (Annotation) i.next();
          annBuffer.append(ann.toString() + " ");
        }
      }
    }
    toolTip = annBuffer.toString();
    if (toolTip.length() != 0)
      jTextPaneRight.setToolTipText(toolTip);
    else
      jTextPaneRight.setToolTipText(null);
  }

}

