package bluej.extensions.editor;

import bluej.editor.*;

/**
 * Proxy object that allows interaction with the BlueJ Editor for a
 * particular class.
 * Most method of this class must be called from a swing compatile thread,
 * if a method is thread safe it will be marked so.
 *
 * @version    $Id: Editor.java 2925 2004-08-22 13:42:42Z damiano $
 */

/*
 * @author Damiano Bolla, University of Kent at Canterbury, 2004
 */
public class Editor
{
    private bluej.editor.Editor bjEditor;


    /**
     * Constructor must not be public.
     * You get an Editor object by calling BClass.getEditor(), which
     * will create a (non-visible) editor if one does not already exist.
     *
     * @param  bjEditor  Description of the Parameter
     */
    Editor( bluej.editor.Editor bjEditor )
    {
        this.bjEditor = bjEditor;
    }


    /**
     * Request to the editor to save the file currently opened.
     */
    public void saveFile()
    {
        bjEditor.save();
    }


    /**
     * Request to the editor to shows or hide this Editor depending on the value of parameter visible.
     *
     * @param  visible  The new visible value
     */
    public void setVisible( boolean visible )
    {
        bjEditor.setVisible( visible );
    }


    /**
     * Returns if this Editor is visible.
     *
     * @return    true if the Editor is visible, false otherwise.
     */
    public boolean isVisible()
    {
        return bjEditor.isShowing();
    }


    /**
     * Returns the current caret location within the edited text.
     *
     * @return    the textLocation.
     */
    public TextLocation getCaretLocation()
    {
        LineColumn lineColumn = bjEditor.getCaretLocation();
        return new TextLocation( lineColumn.getLine(), lineColumn.getColumn() );
    }


    /**
     * Sets the current Caret location within the edited text.
     *
     * @param  location                   The location in the text to set the Caret to.
     * @throws  IllegalArgumentException  if the specified TextLocation represents a position which does not exist in the text.
     */
    public void setCaretLocation( TextLocation location )
    {
        bjEditor.setCaretLocation(convertLocation(location));
    }


    /**
     * Request to the editor to display the given message in the editor message area.
     * The message will be cleared when BlueJ needs to.
     *
     * @param  message  The message to display.
     */
    public void showMessage( String message )
    {
        bjEditor.writeMessage( message );
    }


    /**
     * Returns the location at which current selection begins.
     *
     * @return    the current beginning of the selection or null if no text is selected.
     */
    public TextLocation getSelectionBegin()
    {
        LineColumn lineColumn = bjEditor.getSelectionBegin();
        if ( lineColumn == null ) {
            return null;
        }

        return new TextLocation( lineColumn.getLine(), lineColumn.getColumn() );
    }


    /**
     * Returns the location at which the current selection ends.
     *
     * @return    the current end of the selection or null if no text is selected
     */
    public TextLocation getSelectionEnd()
    {
        LineColumn lineColumn = bjEditor.getSelectionEnd();
        if ( lineColumn == null ) {
            return null;
        }

        return new TextLocation( lineColumn.getLine(), lineColumn.getColumn() );
    }


    /**
     * Returns the text which lies between the two TextLocations.
     *
     * @param  begin                      The beginning of the text to get
     * @param  end                        The end of the text to get
     * @return                            The text value
     * @throws  IllegalArgumentException  if either of the specified TextLocations represent a position which does not exist in the text.
     */
    public String getText( TextLocation begin, TextLocation end )
    {
        return bjEditor.getText(convertLocation(begin),convertLocation(end));
    }


    /**
     * Request to the editor to reeplace the text between beginning and end with the given newText
     * If begin and end are the same object, the text is inserted.
     *
     * @param  begin                      where to start to replace
     * @param  end                        where to end to replace
     * @param  newText                    The new text value
     * @throws  IllegalArgumentException  if either of the specified TextLocations
     * represent a position which does not exist in the text.
     */
    public void setText( TextLocation begin, TextLocation end, String newText )
    {
    }


    /**
     * Request to the editor to mark the text between begin and end as selected.
     *
     * @param  begin                      where to start the selection
     * @param  end                        where to end the selection
     * @throws  IllegalArgumentException  if either of the specified TextLocations
     * represent a position which does not exist in the text.
     */
    public void setSelection( TextLocation begin, TextLocation end )
    {
    }


    /**
     * Request to the editor to permit or deny editor content modification by the user using the graphic user interface.
     * Extensions must set readOnly to true before changing the editor content programmatically.
     *
     * @param  readOnly  If true user cannot change the editor content using the GUI, false allows user interaction using the GUI.
     */
    void setReadOnly( boolean readOnly )
    {
        bjEditor.setReadOnly( readOnly );
    }


    /**
     * Returns if the editor is readonly or not.
     *
     * @return    true if the user cannot change the text using the GUI, false othervise
     */
    boolean isReadonly()
    {
        return bjEditor.isReadOnly();
    }


    /**
     * Returns a property of the current editor.
     * This allows custom versions of the editor to communicate with extensions.
     *
     * @param  propertyKey  The propertyKey of the property to retrieve.
     * @return              the property value or null if it is not found
     */
    public Object getProperty( String propertyKey )
    {
        return null;
    }


    /**
     * Set a property for the current editor. Any existing property with
     * this key will be overwritten.
     *
     * @param  propertyKey  The property key of the new property
     * @param  value        The new property value
     */
    public void setProperty( String propertyKey, Object value )
    {
    }


    /**
     * Translates a text location into an offset into the text held
     * by the editor.
     *
     * @param  location                   position to be translated
     * @return                            the offset into the text of this text
     * @throws  IllegalArgumentException  if the specified TextLocations
     * represent a position which does not exist in the text.
     */
    public int getOffsetFromTextLocation( TextLocation location )
    {
        return bjEditor.getOffsetFromLineColumn( convertLocation(location) );
    }


    /**
     * Translate an offset in the text held by the editor into a text location
     *
     * @param  offset  location to be translated
     * @return         the TextLocation in the text of this offset or null if the offset is invalid
     */
    public TextLocation getTextLocationFromOffset( int offset )
    {
        LineColumn lineColumn = bjEditor.getLineColumnFromOffset( offset );

        if ( lineColumn == null ) {
            return null;
        }

        return new TextLocation( lineColumn.getLine(), lineColumn.getColumn() );
    }


    /**
     * Returns the length of the line indicated in the edited text
     *
     * @param  line  the line in the text for which the length should be calculated
     * @return       the length of the line, -1 if line is invalid
     */
    public int getLineLength( int line )
    {
        return 0;
    }


    /**
     * Returns the total number of lines in the currently edited text
     *
     * @return    The lineCount value
     */
    public int getLineCount()
    {
        return 0;
    }


    /**
     * Utility to convert a TextLocation into a LineColumn.
     *
     * @param  location  The point in the editor to convert to a LineColumn.
     * @return           The LineColumn object describing a point in the editor.
     */
    private LineColumn convertLocation( TextLocation location )
    {
        return new LineColumn( location.getLine(), location.getColumn() );
    }
}

