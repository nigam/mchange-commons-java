/*
 * Distributed as part of mchange-commons-java 0.2.9
 *
 * Copyright (C) 2015 Machinery For Change, Inc.
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

package com.mchange.v1.util;

import java.util.StringTokenizer;

public final class StringTokenizerUtils
{
    public static String[] tokenizeToArray(String str, String delim, boolean returntokens)
    {
	StringTokenizer st = new StringTokenizer(str, delim, returntokens);
	String[] strings = new String[st.countTokens()];
	for (int i = 0; st.hasMoreTokens(); ++i)
	   strings[i] = st.nextToken();
	return strings;
    }

    public static String[] tokenizeToArray(String str, String delim)
    {return tokenizeToArray(str, delim, false);}

    public static String[] tokenizeToArray(String str)
    {return tokenizeToArray(str, " \t\r\n");}
}




