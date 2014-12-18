/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	Main.java - The main class and application entry point

	Created by : Maurizio M. Gavioli 2014-12-16
*****************************/

package com.vistamaresoft.jpdfi;

//import de.intarsys.pdf.pd.PDDocument;
//import de.intarsys.tools.locator.FileLocator;
import com.vistamaresoft.jpdfi.JPDIDocument;

/******************
	CLASS Main
*******************/

public class Main
{
	public static void main(String[] args) throws Exception
	{
		if (args.length < 2)
		{
			usage();
			return;
		}

		JPDIDocument	outDoc	= new JPDIDocument(args[0]);
		if (outDoc.impose())
			outDoc.save(args[1]);
	}

	/******************
		Help the user.
	*******************/

	public static void usage()
	{
		System.out.println("usage: java[.exe] jPDFi <input-pdf> <output-pdf>");
	}
}
