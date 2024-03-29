/**
 * @(#)CommandLine.java	1.10 03/01/23
 *
 * Copyright 2003 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.sun.tools.javac.v8;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;

import com.sun.tools.javac.v8.util.ListBuffer;

/**
 * Various utility methods for processing Java tool command line arguments.
 */
public class CommandLine {

	public CommandLine() {
		super();
	}

	/**
	 * Process Win32-style command files for the specified command line
	 * arguments and return the resulting arguments. A command file argument is
	 * of the form '@file' where 'file' is the name of the file whose contents
	 * are to be parsed for additional arguments. The contents of the command
	 * file are parsed using StreamTokenizer and the original '@file' argument
	 * replaced with the resulting tokens. Recursive command files are not
	 * supported. The '@' character itself can be quoted with the sequence '@@'.
	 */
	public static String[] parse(String[] args) throws IOException {
		ListBuffer newArgs = new ListBuffer();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.length() > 1 && arg.charAt(0) == '@') {
				arg = arg.substring(1);
				if (arg.charAt(0) == '@') {
					newArgs.append(arg);
				} else {
					loadCmdFile(arg, newArgs);
				}
			} else {
				newArgs.append(arg);
			}
		}
		return (String[]) newArgs.toList()
				.toArray(new String[newArgs.length()]);
	}

	private static void loadCmdFile(String name, ListBuffer args)
			throws IOException {
		Reader r = new BufferedReader(new FileReader(name));
		StreamTokenizer st = new StreamTokenizer(r);
		st.resetSyntax();
		st.wordChars(' ', 255);
		st.whitespaceChars(0, ' ');
		st.commentChar('#');
		st.quoteChar('\"');
		st.quoteChar('\'');
		while (st.nextToken() != st.TT_EOF) {
			args.append(st.sval);
		}
		r.close();
	}
}
