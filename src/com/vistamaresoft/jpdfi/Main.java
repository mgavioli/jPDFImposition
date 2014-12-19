/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	Main.java - The main class and application entry point

	Created by : Maurizio M. Gavioli 2014-12-16
*****************************/

package com.vistamaresoft.jpdfi;

import com.vistamaresoft.jpdfi.JPDIDocument;
import java.util.HashMap;

/******************
	CLASS Main
*******************/

public class Main
{
	// CLI
	private static final int		MAX_PARAMS	= 2;
	static int						numOfParams	= 0;
	static String[]					params		= { null, null};
	static HashMap<String, String>	options;
	static String					wrongOption = "";

	public static void main(String[] args) throws Exception
	{
		if (args.length < 2)
		{
			usage();
			return;
		}

		JPDIDocument	outDoc	= new JPDIDocument(params[0]);
		String format = options.get("f");
		if (format != null)
			outDoc.setFormat(format);
		if (outDoc.impose())
			outDoc.save(params[1]);
	}
	/******************
		CLI
	*******************/

	public static boolean parseCL(String[] args)
	{
		final String	acceptedOptions = "fs";
		boolean			isOption	= false;				// true when expecting a string for an option
		char			lastOption	= '\0';
		options = new HashMap<String, String>();

		for (String arg : args)
		{
			if (arg.charAt(0) != '-' && arg.charAt(0) != '/')
			{
				// real parameters (rather than an option value string)
				if (!isOption)
				{
					if (numOfParams < MAX_PARAMS)
					{
						params[numOfParams] = arg;
						numOfParams++;
					}
					else
					{
						System.err.println("Too many parameters.");
						return false;
					}
				}
				else
					options.put(Character.toString(lastOption), arg);
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
				// has an attached value (not separated by a space)
				if (arg.length() > 2)			// if value is directly attached to option char
					options.put(Character.toString(lastOption), arg.substring(2));
				else
					isOption = true;			// expect an option value as next string
			}
		}
		return true;
	}

	/******************
		Usage
	*******************/

	public static void usage()
	{
		System.out.println("usage: java[.exe] jPDFi [options] <input-pdf> <output-pdf>\nWhere options can be:\n-f format\the imposition format\n-s sheetsPerSignature\t the max no. of sheets a signature may have\\n");
	}
}
