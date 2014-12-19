/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	JPDIImpoData.java - Holds the data about where to impose a 'signature-ful' of
			source pages into the destination imposed pages of a signature.

	Created by : Maurizio M. Gavioli 2014-12-17
*****************************/

package com.vistamaresoft.jpdfi;

/******************
	CLASS JPDImpoData
*******************/

public class JPDImpoData
{
	public enum Format
	{
		in4h	(0),	// in 4° format, first fold is horizontal
		in4v	(1),	// in 4° format, first fold is vertical
		in8h	(2),	// in 8° format, first fold is horizontal
		in8v	(3),	// in 8° format, first fold is vertical
		in16h	(4),	// in 16° format, first fold is horizontal
		in16v	(5),	// in 16° format, first fold is vertical
		booklet	(6),	// booklet format (multiple sheets bounded together)
		numOfFormats	(7);

		private final int formatCode;
		Format(int formatCode)	{ this.formatCode = formatCode;	}
		public int code()		{ return this.formatCode; }
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

	private class JPDIPageImpoData
	{
		int	destPage;		/// in which page of the signature the page shall end up (0-based)
		int	row;			/// in which row of that page (0-based)
		int	col;			/// in which column of that page (0-based)
		int	rotation;		// with which rotation (in degrees)
	}
	private JPDIPageImpoData pageImpoData[];

	/******************
		C'tor
	*******************/

	public JPDImpoData(Format format, int sheetsPerSign)
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
			if (sheetsPerSign < 1)	sheetsPerSign = 1;
		}
		if (numOfRows != 1 || numOfCols != 2)
			sheetsPerSign = 1;
		int pagesPerSign = sheetsPerSign * numOfRows * numOfCols * 2;

		// initialize pageImpoData according to format
		pageImpoData = new JPDIPageImpoData[pagesPerSign];
		for (int currPageNo = 0; currPageNo < pageImpoData.length; currPageNo++)
		{
			pageImpoData[currPageNo] = new JPDIPageImpoData();
			// DESTINATION PAGE
			pageImpoData[currPageNo].destPage = sheetsPerSign == 1 ?
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
		if (format == "in4h")
			return Format.in4h;
		else if (format == "in4v")
			return Format.in4v;
		else if (format == "in8h")
			return Format.in8h;
		else if (format == "in8v")
			return Format.in8v;
		else if (format == "in16h")
			return Format.in16h;
		else if (format == "in16v")
			return Format.in16v;
		return Format.booklet;
	}
}
