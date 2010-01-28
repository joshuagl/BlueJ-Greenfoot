/*
 This file is part of the BlueJ program. 
 Copyright (C) 2010  Michael Kolling and John Rosenberg 
 
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
package bluej.pkgmgr;

import bluej.debugger.gentype.Reflective;
import bluej.parser.entity.EntityResolver;
import bluej.parser.entity.JavaEntity;
import bluej.parser.entity.PackageEntity;
import bluej.parser.entity.PackageOrClass;
import bluej.parser.entity.TypeEntity;
import bluej.pkgmgr.target.ClassTarget;
import bluej.pkgmgr.target.Target;

/**
 * Resolve project entities.
 * 
 * @author Davin McCall
 */
public class ProjectEntityResolver implements EntityResolver
{
    private Project project;
    
    /**
     * Construct a ProjectEntityResolver for the given project.
     */
    public ProjectEntityResolver(Project project)
    {
        this.project = project;
    }
    
    public JavaEntity getValueEntity(String name, String querySource)
    {
        return resolvePackageOrClass(name, querySource);
    }

    public PackageOrClass resolvePackageOrClass(String name, String querySource)
    {
        int lastDot = querySource.lastIndexOf('.');
        String pkgName;
        if (lastDot != -1) {
            pkgName = querySource.substring(0, lastDot + 1); // include the dot
        }
        else {
            pkgName = "";
        }
        TypeEntity rval = resolveQualifiedClass(pkgName + name);
        if (rval != null) {
            return rval;
        }

        // Try in java.lang
        try {
            Class<?> cl = project.getClassLoader().loadClass("java.lang." + name);
            return new TypeEntity(cl);
        }
        catch (Exception e) {}
        
        // Have to assume it's a package
        return new PackageEntity(name, this);
    }

    public TypeEntity resolveQualifiedClass(String name)
    {
        int lastDot = name.lastIndexOf('.');
        String pkgName = lastDot != -1 ? name.substring(0, lastDot) : "";
        String baseName = name.substring(lastDot + 1);
        Package pkg = project.getPackage(pkgName);
        if (pkg != null) {
            Target target = pkg.getTarget(baseName);
            if (target instanceof ClassTarget) {
                ClassTarget ct = (ClassTarget) target;
                Reflective ref = ct.getTypeRefelective();
                if (ref != null) {
                    return new TypeEntity(ref);
                }
            }
        }

        try {
            // Try as a class which might be external to the project 
            Class<?> cl = project.getClassLoader().loadClass(name);
            return new TypeEntity(cl);
        }
        catch (Exception e) {}
        
        return null;
    }

}
