package bluej.debugmgr.texteval;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.KeyEvent;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.text.*;

import bluej.BlueJEvent;
import bluej.debugger.DebuggerObject;
import bluej.debugmgr.ExpressionInformation;
import bluej.debugmgr.Invoker;
import bluej.debugmgr.ResultWatcher;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.testmgr.record.InvokerRecord;
import bluej.utility.Debug;
import bluej.utility.JavaNames;

import org.gjt.sp.jedit.syntax.*;

/**
 * A customised text area for use in the BlueJ Java text evaluation.
 *
 * @author  Michael Kolling
 * @version $Id: TextEvalArea.java 2614 2004-06-15 15:37:24Z mik $
 */
public final class TextEvalArea extends JScrollPane
    implements ResultWatcher
{
    private static final int BUFFER_LINES = 40;
    private static final String PROMPT = "> ";
    private static final int PROMPT_LENGTH = 2;
    
//    private JTextArea text;
    private JEditorPane text;
    private DefaultSyntaxDocument doc;  // the text document behind the editor pane
    private String currentCommand;
    private PkgMgrFrame frame;
    private Invoker invoker = null;
    private boolean firstTry;
    
    /**
     * Create a new text area with given size.
     */
    public TextEvalArea(PkgMgrFrame frame, Font font)
    {
        this.frame = frame;
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createBevelBorder(BevelBorder.LOWERED),
                BorderFactory.createEmptyBorder(5,0,5,0)));

//        text = new JTextArea(6,40);
        text = new JEditorPane();
        text.setMargin(new Insets(2,2,2,2));
        text.setEditorKit(new SyntaxEditorKit());

        doc = (DefaultSyntaxDocument) text.getDocument();
        doc.setTokenMarker(new TextEvalTokenMarker());
        doc.setColors(getTextColors());

        setViewportView(text);
        text.setFont(font);
        append(PROMPT);

        KeyStroke enterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        text.getKeymap().addActionForKeyStroke(enterKey, new ExecuteCommandAction(this));
        setVerticalScrollBarPolicy(VERTICAL_SCROLLBAR_ALWAYS);
        setPreferredSize(new Dimension(200,100));

    }

    public void requestFocus()
    {
        text.requestFocus();
    }

    private Color[] getTextColors()
    { 
        Color[] colors = new Color[1];

        colors[0] = new Color(255, 10, 10);
        return colors;
    }

    /**
     * Execute the text of the current line in the text area as a Java command.
     */
    public void executeCommand()
    {
        currentCommand = getCurrentLine();
        append("\n");
        firstTry = true;
        invoker = new Invoker(frame, currentCommand, this);
    }
    
    //   --- ResultWatcher interface ---

    /**
     * An invocation has completed - here is the result.
     * If the invocation has a void result (note that is a void type), result == null.
     */
    public void putResult(DebuggerObject result, String name, InvokerRecord ir)
    {
        frame.getObjectBench().addInteraction(ir);

        if (result != null) {
            //Debug.message("type:"+result.getFieldValueTypeString(0));

            String resultString = result.getFieldValueString(0);
            String resultType = result.getFieldValueTypeString(0);
            
            append(resultString);
            append("   (" + resultType + ")");
            
//            ResultInspector viewer = ResultInspector.getInstance(result, name,
//                    getPackage(), ir, getExpressionInformation(), PkgMgrFrame.this);
            BlueJEvent.raiseEvent(BlueJEvent.METHOD_CALL, resultString);
//        	String fieldString =  JavaNames.stripPrefix(result.getFieldValueTypeString(0))        
//            + " = " + obj.getFieldValueString(0);
//            
        } else {
            BlueJEvent.raiseEvent(BlueJEvent.METHOD_CALL, null);
        }
        append("\n" + PROMPT);
    }
    
    /**
     * An invocation has failed - here is the error message
     */
    public void putError(String message)
    {
    		if(firstTry) {
    			append("   --error: " + message + "\n");
    			firstTry = false;
    	        invoker.tryAgain();
    		}
    		else {
    			append("Error: " + message);
    			append("\n" + PROMPT);
    		}
    }
    
    /**
     * A watcher shuold be able to return information about the result that it
     * is watching. T is used to display extra information (about the expression
     * that gave the result) when the result is shown.
     * 
     * @return An object with information on the expression
     */
    public ExpressionInformation getExpressionInformation()
    {
        return new ExpressionInformation(currentCommand);
    }

    //   --- end of ResultWatcher interface ---


/**
     * Append some text to this area.
     * @param s The text to append.
     */
    public void append(String s)
    {
        try {
            doc.insertString(doc.getLength(), s, null);
            caretToEnd();
            
//            int lines = text.getLineCount();
//            if(lines > BUFFER_LINES) {
//                int linePos = text.getLineStartOffset(lines-BUFFER_LINES);
//                text.replaceRange("", 0, linePos);
//            }
        }
        catch(BadLocationException exc) {
            Debug.reportError("bad location in terminal operation");
        }
    }
    
    public void caretToEnd() {
        text.setCaretPosition(doc.getLength());
    }
    
    /**
     * Get the text of the current line (the last line) of this area.
     * @return The text of the last line.
     */
    private String getCurrentLine()
    {
        Element line = doc.getParagraphElement(doc.getLength());
        int lineStart = line.getStartOffset() + PROMPT_LENGTH;
        int lineEnd = line.getEndOffset();
        
        try {
            return doc.getText(lineStart, lineEnd-lineStart);
        }
        catch(BadLocationException exc) {
            Debug.reportError("bad location in text eval operation");
            return "";
        }
    }
}
