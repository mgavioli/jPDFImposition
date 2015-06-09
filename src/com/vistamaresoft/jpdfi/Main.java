/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	Main.java - The main class and application entry point

	Created by : Maurizio M. Gavioli 2014-12-16

	This program is free software; you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation; either version 2 of the License, or
	(at your option) any later version.
	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
	GNU General Public License for more details.
	You should have received a copy of the GNU General Public License along
	with this program; if not, write to the Free Software Foundation, Inc.,
	51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

*****************************/

package com.vistamaresoft.jpdfi;

import com.vistamaresoft.jpdfi.JPDIDocument;
import java.util.HashMap;

/******************
	CLASS Main
*******************/

public class Main
{
	public static final String		version	= "0.6.0";

	// CLI
	static HashMap<String, String>	options;
	static String					wrongOption = "";

	public static void main(String[] args) //throws Exception
	{
		boolean parsed = parseCL(args);
		if (!wrongOption.isEmpty())
			System.err.println("Unknown option '" + wrongOption + ".\n");
		if (!wrongOption.isEmpty() || !parsed)
			usage();
		
		JPDIDocument	outDoc	= new JPDIDocument();
		// set provided options into the document
		if (options.get("i") != null)
			outDoc.addSourceFileName(options.get("i"));
		if (options.get("o") != null)
			outDoc.setOutputFileName(options.get("o"));
		if (options.get("f") != null)
			outDoc.setFormat(options.get("f"), options.get("s"));
		else if (options.get("s") != null)
			outDoc.setMaxSheetsPerSign(options.get("s"));
		// leave -l as last, to overwrite any CLI option
		if (options.get("l") != null)
			if (!outDoc.readParamFile(options.get("l")))
				System.exit(1);
		System.out.println(outDoc.inputFileNames() + " => " + outDoc.outputFileName() + "\n");
		if (outDoc.impose())
			outDoc.save();
	}

	/******************
		CLI
	*******************/

	protected static boolean parseCL(String[] args)
	{
		final String	acceptedOptions = "fhliosv";
		boolean			isOption	= false;				// true when expecting a string for an option
		char			lastOption	= '\0';
		options = new HashMap<String, String>();

		for (String arg : args)
		{
			if (arg.charAt(0) != '-')
			{
				// real parameters (rather than an option value string)
				if (!isOption)
				{
					System.err.println("Option missing for parameter \"" + arg + "\".");
					return false;
				}
				else
				{
					options.put(Character.toString(lastOption), arg);
					isOption = false;
				}
			}
			else
			{
				lastOption = arg.charAt(1);
				// is a legal option?
				if (acceptedOptions.indexOf(lastOption, 0) == -1)
				{
					wrongOption = arg;
					return false;
				}
				// locally managed options:
				switch (lastOption)
				{
				case 'h':
					usage();
					break;
				case 'v':
					version();
					break;
				default:
					// has an attached value (not separated by a space)
					if (arg.length() > 2)			// if value is directly attached to option char
						options.put(Character.toString(lastOption), arg.substring(2));
					else
						isOption = true;			// expect an option value as next string
					break;
				}
			}
		}
		return true;
	}

	/******************
		Usage
	*******************/

	public static void usage()
	{
		version();
		System.out.println("usage: java[.exe] jPDFImposition [options]\n" +
				"Where options can be:\n-f format\the imposition format\n" +
				"-s sheetsPerSignature\tthe max no. of sheets a signature may have\n" +
				"-l filename\tan XML parameter file with additional parameters\n" +
				"-i filename\tthe input PDF file name\n" +
				"-o filename\tthe output PDF file name\n");
	}
	/******************
		Usage
	*******************/

	public static void version()
	{
		System.out.println("jPDFImposition " + version + "\n" +
			"(C) Copyright 2015, Maurizio M. Gavioli");
	}
}
