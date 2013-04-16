/*
 * Distributed as part of mchange-commons-java v.0.2.4
 *
 * Copyright (C) 2013 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of EITHER:
 *
 *     1) The GNU Lesser General Public License (LGPL), version 2.1, as 
 *        published by the Free Software Foundation
 *
 * OR
 *
 *     2) The Eclipse Public License (EPL), version 1.0
 *
 * You may choose which license to accept if you wish to redistribute
 * or modify this work. You may offer derivatives of this work
 * under the license you have chosen, or you may provide the same
 * choice of license which you have been offered here.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received copies of both LGPL v2.1 and EPL v1.0
 * along with this software; see the files LICENSE-EPL and LICENSE-LGPL.
 * If not, the text of these licenses are currently available at
 *
 * LGPL v2.1: http://www.gnu.org/licenses/old-licenses/lgpl-2.1.html
 *  EPL v1.0: http://www.eclipse.org/org/documents/epl-v10.php 
 * 
 */

package com.mchange.v2.cfg;

import java.util.*;
import java.io.*;
import com.mchange.v2.log.*;

/**
 * MultiPropertiesConfig allows applications to accept configuration data
 * from a more than one property file (each of which is to be loaded from
 * a unique path using this class' ClassLoader's resource-loading mechanism),
 * and permits access to property data via the resource path from which the
 * properties were loaded, via the prefix of the property (where hierarchical 
 * property names are presumed to be '.'-separated), and simply by key.
 * In the by-key and by-prefix indices, when two definitions conflict, the
 * key value pairing specified in the MOST RECENT properties file shadows
 * earlier definitions, and files are loaded in the order of the list of
 * resource paths provided a constructor.
 *
 * The resource path "/" is a special case that always refers to System
 * properties. No actual resource will be loaded.
 *
 * If the mchange-hocon-bridge jar file is available, resource paths specified
 * as "hocon:/path/to/resource" will be parsed as 
 * <a href="https://github.com/typesafehub/config/blob/master/HOCON.md">HOCON</a>,
 * whenever values can be interpreted as Strings. 
 *
 * The class manages a special instance called "vmConfig" which is accessable
 * via a static method. It's resource path is list specified by a text-file,
 * itself a ClassLoader managed resource, which may be located at
 * <tt>/com/mchange/v2/cfg/vmConfigResourcePaths.txt</tt> or <tt>/mchange-config-resource-paths.txt</tt>.
 * This file should
 * be one resource path per line, with blank lines ignored and lines beginning
 * with '#' treated as comments.
 *
 * If no text file of resource paths are available, the following resources are
 * checked: "hocon:/reference.conf", "/mchange-commons.properties", "hocon:/application.conf", "/"
 */
public abstract class MultiPropertiesConfig implements PropertiesConfig
{
    final static MultiPropertiesConfig EMPTY = new BasicMultiPropertiesConfig( new String[0] );

    final static String[] DFLT_VM_RSRC_PATHFILES    = new String[] {"/com/mchange/v2/cfg/vmConfigResourcePaths.txt", "/mchange-config-resource-paths.txt"};
    final static String[] HARDCODED_DFLT_RSRC_PATHS = new String[] {"hocon:/reference.conf", "/mchange-commons.properties", "hocon:/application.conf", "/"};
    final static String[] NO_PATHS                  = new String[0];

    //MT: protected by class' lock
    static MultiPropertiesConfig vmConfig = null;

    public static MultiPropertiesConfig read(String[] resourcePath, MLogger logger)
    { return new BasicMultiPropertiesConfig( resourcePath, logger ); }

    public static MultiPropertiesConfig read(String[] resourcePath)
    { return new BasicMultiPropertiesConfig( resourcePath ); }

    public static MultiPropertiesConfig combine( MultiPropertiesConfig[] configs )
    { return new CombinedMultiPropertiesConfig( configs ).toBasic(); }

    public static MultiPropertiesConfig readVmConfig(String[] defaultResources, String[] preemptingResources )
    { return readVmConfig( defaultResources, preemptingResources, (List) null ); }

    public static MultiPropertiesConfig readVmConfig(String[] defaultResources, String[] preemptingResources, MLogger logger)
    {
	List items = new ArrayList();
	MultiPropertiesConfig out = readVmConfig( defaultResources, preemptingResources, items );
	items.addAll( out.getDelayedLogItems() );
	for (Iterator ii = items.iterator(); ii.hasNext(); )
	{
	    DelayedLogItem item = (DelayedLogItem) ii.next();
	    logger.log( item.getLevel(), item.getText(), item.getException() );
	}
	return out;
    }

    public static MultiPropertiesConfig readVmConfig(String[] defaultResources, String[] preemptingResources, List delayedLogItemsOut)
    {
	defaultResources = ( defaultResources == null ? NO_PATHS : defaultResources );
	preemptingResources = ( preemptingResources == null ? NO_PATHS : preemptingResources );
	List pathsList = condensePaths( new String[][]{ defaultResources, vmResourcePaths( delayedLogItemsOut ), preemptingResources } );
	
	if ( delayedLogItemsOut != null )
	{
	    StringBuffer sb = new StringBuffer(2048);
	    for ( int i = 0, len = pathsList.size(); i < len; ++i)
	    {
		if ( i != 0 ) sb.append(", ");
		sb.append( pathsList.get(i) );
	    }

	    delayedLogItemsOut.add( new DelayedLogItem(MLevel.FINER, "Reading VM config for path list " + sb.toString() ) );
	}

	return read( (String[]) pathsList.toArray(new String[pathsList.size()]) );
    }

    private static List condensePaths(String[][] pathLists)
    {
	// we do this in reverse, so that the "first" time
	// we encounter a path becomes the last in the resultant
	// list. that is, we want redundantly specified paths 
	// to have their maximum specified preference

	Set pathSet = new HashSet();
	List reverseMe = new ArrayList();
	for ( int i = pathLists.length; --i >= 0; )
	    for( int j = pathLists[i].length; --j >= 0; )
	    {
		String path = pathLists[i][j];
		if (! pathSet.contains( path ) )
		{
		    pathSet.add( path );
		    reverseMe.add( path );
		}
	    }
	 Collections.reverse( reverseMe );
	 return reverseMe;
    }

    private static List readResourcePathsFromResourcePathsTextFile( String resourcePathsTextFileResourcePath,  List delayedLogItemsOut )
    {
	List rps = new ArrayList();

	BufferedReader br = null;
	try
	    {
		InputStream is = MultiPropertiesConfig.class.getResourceAsStream( resourcePathsTextFileResourcePath );
		if ( is != null )
		    {
			br = new BufferedReader( new InputStreamReader( is, "8859_1" ) );
			String rp;
			while ((rp = br.readLine()) != null)
			    {
				rp = rp.trim();
				if ("".equals( rp ) || rp.startsWith("#"))
				    continue;
				
				rps.add( rp );
			    }

			if ( delayedLogItemsOut != null )
			    delayedLogItemsOut.add( new DelayedLogItem( MLevel.FINEST, String.format( "Added paths from resource path text file at '%s'", resourcePathsTextFileResourcePath ) ) );
		    }
		else if ( delayedLogItemsOut != null )
		    delayedLogItemsOut.add( new DelayedLogItem( MLevel.FINEST, String.format( "Could not find resource path text file for path '%s'", resourcePathsTextFileResourcePath ) ) );

	    }
	catch (IOException e)
	    { e.printStackTrace(); }
	finally
	    {
		try { if ( br != null ) br.close(); }
		catch (IOException e) { e.printStackTrace(); }
	    }

	return rps;
    }

    private static List readResourcePathsFromResourcePathsTextFiles( String[] resourcePathsTextFileResourcePaths, List delayedLogItemsOut )
    {
	List out = new ArrayList();
	for ( int i = 0, len = resourcePathsTextFileResourcePaths.length; i < len; ++i )
	    out.addAll( readResourcePathsFromResourcePathsTextFile(  resourcePathsTextFileResourcePaths[i], delayedLogItemsOut ) );
	return out;
    }

    private static String[] vmResourcePaths( List delayedLogItemsOut ) 
    {
	List paths = vmResourcePathList(  delayedLogItemsOut );
	return (String[]) paths.toArray( new String[ paths.size() ] );
    }

    private static List vmResourcePathList( List delayedLogItemsOut )
    {
	List pathsFromFiles = readResourcePathsFromResourcePathsTextFiles( DFLT_VM_RSRC_PATHFILES, delayedLogItemsOut );
	List rps;
	if ( pathsFromFiles.size() > 0 )
	    rps = pathsFromFiles;
	else
	    rps = Arrays.asList( HARDCODED_DFLT_RSRC_PATHS );
	return rps;
    }
    
    public synchronized static MultiPropertiesConfig readVmConfig()
    { return readVmConfig( (List) null ); }

    public synchronized static MultiPropertiesConfig readVmConfig( MLogger logger )
    {
	List items = new ArrayList();
	MultiPropertiesConfig out = readVmConfig( items );
	items.addAll( out.getDelayedLogItems() );
	for (Iterator ii = items.iterator(); ii.hasNext(); )
	{
	    DelayedLogItem item = (DelayedLogItem) ii.next();
	    logger.log( item.getLevel(), item.getText(), item.getException() );
	}
	return out;
    }

    public synchronized static MultiPropertiesConfig readVmConfig( List delayedLogItemsOut )
    {
	if ( vmConfig == null )
	    {
		List rps = vmResourcePathList( delayedLogItemsOut );
		vmConfig = new BasicMultiPropertiesConfig( (String[]) rps.toArray( new String[ rps.size() ] ) ); 
	    }
	return vmConfig;
    }

    public static synchronized boolean foundVmConfig()
    { return vmConfig != EMPTY; }

    public abstract String[] getPropertiesResourcePaths();

    public abstract Properties getPropertiesByResourcePath(String path);

    public abstract Properties getPropertiesByPrefix(String pfx);

//    public abstract Properties getProperties( String key );

    public abstract String getProperty( String key );

    public abstract List getDelayedLogItems();

}
