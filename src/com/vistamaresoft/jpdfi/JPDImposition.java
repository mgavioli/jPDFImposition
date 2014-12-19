/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	JPDImposition.java - Describes an imposition

	Created by : Maurizio M. Gavioli 2014-12-17
*****************************/

package com.vistamaresoft.jpdfi;

//import com.vistamaresoft.jpdfi.JPDImpoData.JPDIPageImpoData;

/******************
	CLASS JPDImposition
*******************/

public class JPDImposition
{
	public enum Format
	{
		in4h	(0),	// in 4° format, first fold is horizontal
		in4v	(1),	// in 4° format, first fold is vertical
		in8h	(2),	// in 8° format, first fold is horizontal
		in8v	(3),	// in 8° format, first fold is vertical
		in16h	(4),	// in 16° format, first fold is horizontal
		in16v	(5),	// in 16° format, first fold is vertical
		booklet	(6);	// booklet format (multiple sheets bounded together)

		private final int formatCode;
		Format(int formatCode)	{ this.formatCode = formatCode;	}
		public int code()		{ return this.formatCode; }
	}

	private class JPDIPageImpoData
	{
		int	destPage;		/// in which page of the signature the page shall end up (0-based)
		int	row;			/// in which row of that page (0-based)
		int	col;			/// in which column of that page (0-based)
		int	rotation;		// with which rotation (in degrees)
	}

	// built-in arrays defining the destination row and column for each of the source pages
	// making up a signature in non-booklet formats (data for booklets are easier to compute)
	private static final int[][] builtinRowData =
{
	{ 0, 0, 1, 1, 1, 1, 0, 0, },	// in 4° h.
	{ 0, 0, 0, 0, 1, 1, 1, 1, },	// in 4° v.
	{ 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0},	// in 8° h.
	{ 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0},	// in 8° v.
	{ 0, 0, 3, 3, 3, 3, 0, 0, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 0, 0, 3, 3, 3, 3, 0, 0},	// in 16° h.
	{ 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 3, 3, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1},	// in 16° v.
};
	private static final int[][] builtinColData =
{
	{ 1, 0, 0, 1, 0, 1, 1, 0, },	// in 4° h.
	{ 1, 0, 1, 0, 0, 1, 0, 1, },	// in 4° v.
	{ 3, 0, 0, 3, 0, 3, 3, 0, 1, 2, 2, 1, 2, 1, 1, 2},	// in 8° h.
	{ 3, 0, 3, 0, 0, 3, 0, 3, 2, 1, 2, 1, 1, 2, 1, 2},	// in 8° v.
	{ 3, 0, 0, 3, 0, 3, 3, 0, 0, 3, 3, 0, 3, 0, 0, 3, 2, 1, 1, 2, 1, 2, 2, 1, 1, 2, 2, 1, 2, 1, 1, 2},	// in 16° h.
	{ 3, 0, 3, 0, 0, 3, 0, 3, 2, 1, 2, 1, 1, 2, 1, 2, 2, 1, 2, 1, 1, 2, 1, 2, 3, 0, 3, 0, 0, 3, 0, 3},	// in 16° v.
};

	private Format				format				= Format.booklet;
	private int					maxSheetsPerSign	= 5;
	private JPDIPageImpoData	pageImpoData[];

	/******************
		Default C'tor
	*******************/
/*
	public JPDImposition()
	{
	}	
*/
	/******************
		Getters / Setters
	*******************/

	public Format	format()			{ return format;				}
	public int 		numOfCols()
	{
		switch (format)
		{
		case in8h:
		case in8v:
		case in16h:
		case in16v:
			return 4;
		default:				// any other supported format has 2 columns 
			return 2;
		}
	}
	public int numOfRows()
	{
		switch (format)
		{
		case in4h:
		case in4v:
		case in8h:
		case in8v:
			return 2;
		case in16h:
		case in16v:
			return 4;
		default:				// any other format defaults to booklet format
			return 1;
		}
	}
	public int maxSheetsPerSignature()				{ return maxSheetsPerSign;	}
/*
	public void setMaxSheetsPerSignature(int val)
	{
		maxSheetsPerSign = val;
		if (maxSheetsPerSign != 1)
			format = Format.booklet;
	}
*/
	public void setFormat(Format formalVal, int maxSheetsPerSignVal)
	{
		format = formalVal;
		maxSheetsPerSign = (format == Format.booklet) ? maxSheetsPerSignVal : 1;
		applyFormat();
	}

	public void setFormat(String formatStr, String maxSheetsPerSignStr)
	{
		Format formatVal = formatStringToVal(formatStr);
		// look for a max sheets per sign value in its specific option, if supplied
		// or as a number in the format option
		String maxSheetStr = (maxSheetsPerSignStr != null) ? maxSheetsPerSignStr : formatStr;
		int maxSheetsPerSignVal = 1;
		try {
			maxSheetsPerSignVal = Integer.parseInt(maxSheetStr);
		}
		catch(NumberFormatException e) {
			maxSheetsPerSignVal = 1;
		}
		finally {
			setFormat(formatVal, maxSheetsPerSignVal);
		}
	}

	private void applyFormat()
	{
		int		numOfCols, numOfRows;
		
		// set number of rows and columns according to format
		switch (format)
		{
		case in4h:
		case in4v:
			numOfRows = numOfCols = 2;
			break;
		case in8h:
		case in8v:
			numOfRows = 2;
			numOfCols = 4;
			break;
		case in16h:
		case in16v:
			numOfRows = numOfCols = 4;
			break;
		default:				// any other format defaults to booklet format
			format = Format.booklet;
			numOfRows = 1;
			numOfCols = 2;
			if (maxSheetsPerSign < 1)	maxSheetsPerSign = 1;
		}
		if (numOfRows != 1 || numOfCols != 2)
			maxSheetsPerSign = 1;
		int pagesPerSign = maxSheetsPerSign * numOfRows * numOfCols * 2;

		// initialize pageImpoData according to format
		pageImpoData = new JPDIPageImpoData[pagesPerSign];
		for (int currPageNo = 0; currPageNo < pageImpoData.length; currPageNo++)
		{
			pageImpoData[currPageNo] = new JPDIPageImpoData();
			// DESTINATION PAGE
			pageImpoData[currPageNo].destPage = maxSheetsPerSign == 1 ?
					// if 1 sheet x sign. (any non-booklet or in-folio booklet):
					//	dest. page either 0 or 1, according to pagenum / 2 is even or odd
					((currPageNo+1) / 2) & 0x0001
					// several sheets x sign. (booklet):
					//	dest. page first increases then decreases
					: Math.min (currPageNo, pagesPerSign-1 - currPageNo);

			// DESTINATION ROW
			pageImpoData[currPageNo].row = format == Format.booklet ?
					// if 1 row x sheet: row always = 0
					0
					// several rows per sheet:
					: builtinRowData[format.code()][currPageNo];

			// ROTATION: 0 for even rows, 180 for odd rows
			pageImpoData[currPageNo].rotation = (pageImpoData[currPageNo].row & 1) != 0 ? 180 : 0;

			// DESTINATION COLUMN
			pageImpoData[currPageNo].col = format == Format.booklet ?
					// column alternates 1 & 0
					1 - (currPageNo & 1)
					// single sheets per sign.:
					: builtinColData[format.code()][currPageNo];
		}
	}

	/******************
		Getters for individual page destination parameters
	*******************/

	public int pageDestPage(int srcPageNo)
	{
		if (srcPageNo < 0 || srcPageNo > pageImpoData.length-1)
			srcPageNo = 0;
		return pageImpoData[srcPageNo].destPage;
	}
	public int pageDestRow(int srcPageNo)
	{
		if (srcPageNo < 0 || srcPageNo > pageImpoData.length-1)
			srcPageNo = 0;
		return pageImpoData[srcPageNo].row;
	}
	public int pageDestCol(int srcPageNo)
	{
		if (srcPageNo < 0 || srcPageNo > pageImpoData.length-1)
			srcPageNo = 0;
		return pageImpoData[srcPageNo].col;
	}
	public int pageDestRotation(int srcPageNo)
	{
		if (srcPageNo < 0 || srcPageNo > pageImpoData.length-1)
			srcPageNo = 0;
		return pageImpoData[srcPageNo].rotation;
	}

	/******************
		STATIC - Convert between numeric and textual representations of formats
	*******************/

	public static String formatValToString(Format format)
	{
		switch (format)
		{
		case in4h:
			return "in4h";
		case in4v:
			return "in4v";
		case in8h:
			return "in8h";
		case in8v:
			return "in8v";
		case in16h:
			return "in16h";
		case in16v:
			return "in16v";
		default:				// any other supported format has 2 columns 
			return "booklet";
		}
	}

	public static Format formatStringToVal(String format)
	{
		format = format.toLowerCase();
		if (format.equals("in4h"))
			return Format.in4h;
		else if (format.equals("in4v"))
			return Format.in4v;
		else if (format.equals("in8h"))
			return Format.in8h;
		else if (format.equals("in8v"))
			return Format.in8v;
		else if (format.equals("in16h"))
			return Format.in16h;
		else if (format.equals("in16v"))
			return Format.in16v;
		return Format.booklet;
	}
}
