/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009  Michael K�lling and John Rosenberg 
 
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
package bluej.pkgmgr.actions;

import bluej.pkgmgr.PkgMgrFrame;

/**
 * Restart VM. Restarts the VM in which objects on the object bench live
 * (and in which programs running under BlueJ execute). This also removes
 * objects from the bench.
 * 
 * @author Davin McCall
 * @version $Id: RestartVMAction.java 6164 2009-02-19 18:11:32Z polle $
 */
final public class RestartVMAction extends PkgMgrAction
{    
    static private RestartVMAction instance = null;
    
    /**
     * Factory method. This is the way to retrieve an instance of the class,
     * as the constructor is private.
     * @return an instance of the class.
     */
    static public RestartVMAction getInstance()
    {
        if(instance == null)
            instance = new RestartVMAction();
        return instance;
    }
    
    private RestartVMAction()
    {
        super("workIndicator.resetMachine");
    }
    
    public void actionPerformed(PkgMgrFrame pmf)
    {
        pmf.restartDebugger();
    }
}
