/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2012,2013,2015,2016  Michael Kolling and John Rosenberg 
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.pkgmgr.dependency;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.Properties;

import javax.swing.*;
import javax.swing.AbstractAction;

import bluej.Config;
import bluej.extensions.BDependency;
import bluej.extensions.BDependency.Type;
import bluej.extensions.ExtensionBridge;
import bluej.extensions.event.DependencyEvent;
import bluej.extmgr.ExtensionsManager;
import bluej.pkgmgr.Package;
import bluej.pkgmgr.PackageEditor;
import bluej.pkgmgr.target.*;
import bluej.utility.Debug;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A dependency between two targets in a package.
 * 
 * @author Michael Cahill
 * @author Michael Kolling
 */
@OnThread(Tag.FXPlatform)
public abstract class Dependency
{
    @OnThread(Tag.Any)
    public final Target from;
    @OnThread(Tag.Any)
    public final Target to;
    @OnThread(Tag.Any)
    private boolean visible = true;
    Package pkg;
    private static final String removeStr = Config.getString("pkgmgr.classmenu.remove");
    protected boolean selected = false;
    //    protected static final float strokeWithDefault = 1.0f;
    //    protected static final float strokeWithSelected = 2.0f;
    @OnThread(Tag.Swing)
    private BDependency singleBDependency; // every Dependency has none or one BDependency

    static final int SELECT_DIST = 4;

    @OnThread(Tag.Any)
    public Dependency(Package pkg, DependentTarget from, DependentTarget to)
    {
        this.from = from;
        this.to = to;
        this.pkg = pkg;
    }

    @OnThread(Tag.Any)
    public Dependency(Package pkg)
    {
        this(pkg, (DependentTarget)null, null);
    }

    @Override
    @OnThread(Tag.Any)
    public boolean equals(Object other)
    {
        if (!(other instanceof Dependency))
            return false;
        Dependency d = (Dependency) other;
        return (d != null) && (d.from == from) && (d.to == to);
    }

    @Override
    @OnThread(Tag.Any)
    public int hashCode()
    {
        return to.hashCode() - from.hashCode();
    }

    @OnThread(Tag.Any)
    public DependentTarget getFrom()
    {
        return (DependentTarget) from;
    }

    @OnThread(Tag.Any)
    public DependentTarget getTo()
    {
        return (DependentTarget) to;
    }

    @OnThread(Tag.Swing)
    public BDependency getBDependency()
    {
        if (singleBDependency == null) {
            singleBDependency = ExtensionBridge.newBDependency(this, getType());
        }

        return singleBDependency;
    }

    /**
     * Returns the type of this dependency. This information is used by
     * extensions to distinguish between the different types of dependencies.
     * Subclasses must implement this method and return an appropriate constant
     * of {@link bluej.extensions.BDependency.Type}.
     * 
     * @return The type of this dependency;
     */
    @OnThread(Tag.Any)
    public abstract Type getType();

    /**
     * Determine the dependency's "to" and "from" nodes by loading their names from the
     * given Properties.
     * 
     * @return true if successful or false if the named targets could not be found
     */
    @OnThread(Tag.Any)
    public Dependency(Package pkg, Properties props, String prefix) throws DependencyNotFoundException
    {
        this.pkg = pkg;
        String fromName = props.getProperty(prefix + ".from");
        if (fromName == null) {
            throw new DependencyNotFoundException("No 'from' target specified for dependency " + prefix);
        }
        this.from = pkg.getTarget(fromName);
        if (! (this.from instanceof DependentTarget)) {
            throw new DependencyNotFoundException("Failed to find 'from' target " + fromName);
        }
                
        String toName = props.getProperty(prefix + ".to");
        if (toName == null) {
            throw new DependencyNotFoundException("No 'to' target specified for dependency " + prefix);
        }
        this.to = pkg.getTarget(toName);
        if (! (this.to instanceof DependentTarget)) {
            throw new DependencyNotFoundException("Failed to find 'to' target " + toName);
        }
    }

    @OnThread(Tag.Swing)
    public void save(Properties props, String prefix)
    {
        props.put(prefix + ".from", ((DependentTarget) from).getIdentifierName());
        props.put(prefix + ".to", ((DependentTarget) to).getIdentifierName());
    }

    /**
     * Disply the context menu.
     */
    public void popupMenu(int x, int y, PackageEditor graphEditor)
    {
        //JPopupMenu menu = new JPopupMenu();
        //menu.add(new RemoveAction());
        //menu.show(graphEditor, x, y);
    }

    /**
     * Remove this element from the graph.
     */
    abstract public void remove();

    public String toString()
    {
        return getFrom().getIdentifierName() + " --> " + getTo().getIdentifierName();
    }

    @OnThread(Tag.Any)
    public boolean isVisible()
    {
        return visible;
    }
    
    public void setVisible(boolean vis)
    {
        if (vis != this.visible) {
            this.visible = vis;
            pkg.repaint();
            
            SwingUtilities.invokeLater(() -> {
                // Inform all listeners about the visibility change
                DependencyEvent event = new DependencyEvent(this, getFrom().getPackage(), vis);
                ExtensionsManager.getInstance().delegateEvent(event);
            });
        }
    }

    public void setSelected(boolean selected)
    {
        this.selected = selected;
        pkg.repaint();
    }

    public boolean isSelected()
    {
        return selected;
    }


    /**
     * Contains method for dependencies that are drawn as more or less straight
     * lines (e.g. extends). Should be overwritten for dependencies with
     * different shape.
     */
    public boolean contains(int x, int y)
    {
        Line line = computeLine();
        Rectangle bounds = getBoxFromLine(line);

        // Now check if <p> is in the rectangle
        if (!bounds.contains(x, y)) {
            return false;
        }

        // Get the angle of the line from pFrom to p
        double theta = Math.atan2(-(line.from.y - y), line.from.x - x);

        double norm = normDist(line.from.x, line.from.y, x, y, Math.sin(line.angle - theta));
        return (norm < SELECT_DIST * SELECT_DIST);
    }

    static final double normDist(int ax, int ay, int bx, int by, double scale)
    {
        return ((ax - bx) * (ax - bx) + (ay - by) * (ay - by)) * scale * scale;
    }

    /**
     * Given the line describing start and end points of this dependency, return
     * its bounding box.
     */
    protected Rectangle getBoxFromLine(Line line)
    {
        int x = Math.min(line.from.x, line.to.x) - SELECT_DIST;
        int y = Math.min(line.from.y, line.to.y) - SELECT_DIST;
        int width = Math.max(line.from.x, line.to.x) - x + (2*SELECT_DIST);
        int height = Math.max(line.from.y, line.to.y) - y + (2*SELECT_DIST);

        return new Rectangle(x, y, width, height);
    }

    /**
     * Compute line information (start point, end point, angle) for the current
     * state of this dependency. This is accurate for dependencis that are drawn
     * as straight lines from and to the target border (such as extends
     * dependencies) and should be redefined for different shaped dependencies.
     */
    public Line computeLine()
    {
        // Compute centre points of source and dest target
        Point pFrom = new Point(from.getX() + from.getWidth() / 2, from.getY() + from.getHeight() / 2);
        Point pTo = new Point(to.getX() + to.getWidth() / 2, to.getY() + to.getHeight() / 2);

        // Get the angle of the line from pFrom to pTo.
        double angle = Math.atan2(-(pFrom.y - pTo.y), pFrom.x - pTo.x);

        // Compute intersection points with target border
        pFrom = ((DependentTarget) from).getAttachment(angle + Math.PI);
        pTo = ((DependentTarget) to).getAttachment(angle);

        return new Line(pFrom, pTo, angle);
    }

    /**
     * Inner class to describe the most important state of this dependency
     * (start point, end point, angle) concisely.
     */
    @OnThread(Tag.FXPlatform)
    public class Line
    {
        public Point from;
        public Point to;
        double angle;

        Line(Point from, Point to, double angle)
        {
            this.from = from;
            this.to = to;
            this.angle = angle;
        }
    }

    @OnThread(Tag.Any)
    public static class DependencyNotFoundException extends Exception
    {
        public DependencyNotFoundException(String s)
        {
            super(s);
        }
    }
}