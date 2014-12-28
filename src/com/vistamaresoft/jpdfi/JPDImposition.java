/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	JPDImposition.java - Describes an imposition

	Created by : Maurizio M. Gavioli 2014-12-17
*****************************/

package com.vistamaresoft.jpdfi;

/******************
	CLASS JPDImposition
*******************/

public class JPDImposition
{
public static final int	DEFAULT_SHEETS_PER_SIGN	= 5;
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

// pre-built arrays defining the destination row and column for each of the source pages
// making up a signature in non-booklet formats (data for booklets are easier to compute)
private static final int[][] prebuiltRowData =
{
	{ 0, 0, 1, 1, 1, 1, 0, 0, },	// in 4° h.
	{ 0, 0, 0, 0, 1, 1, 1, 1, },	// in 4° v.
	{ 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0},	// in 8° h.
	{ 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0},	// in 8° v.
	{ 0, 0, 3, 3, 3, 3, 0, 0, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 0, 0, 3, 3, 3, 3, 0, 0},	// in 16° h.
	{ 0, 0, 0, 0, 3, 3, 3, 3, 3, 3, 3, 3, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1},	// in 16° v.
};
private static final int[][] prebuiltColData =
{
	{ 1, 0, 0, 1, 0, 1, 1, 0, },	// in 4° h.
	{ 1, 0, 1, 0, 0, 1, 0, 1, },	// in 4° v.
	{ 3, 0, 0, 3, 0, 3, 3, 0, 1, 2, 2, 1, 2, 1, 1, 2},	// in 8° h.
	{ 3, 0, 3, 0, 0, 3, 0, 3, 2, 1, 2, 1, 1, 2, 1, 2},	// in 8° v.
	{ 3, 0, 0, 3, 0, 3, 3, 0, 0, 3, 3, 0, 3, 0, 0, 3, 2, 1, 1, 2, 1, 2, 2, 1, 1, 2, 2, 1, 2, 1, 1, 2},	// in 16° h.
	{ 3, 0, 3, 0, 0, 3, 0, 3, 2, 1, 2, 1, 1, 2, 1, 2, 2, 1, 2, 1, 1, 2, 1, 2, 3, 0, 3, 0, 0, 3, 0, 3},	// in 16° v.
};

private Format				format				= Format.booklet;
private int					maxSheetsPerSign	= DEFAULT_SHEETS_PER_SIGN;	// max num. of sheets per signature
// for each source page of each signature, define where and how to place it into the destination
private JPDIPageImpoData	pageImpoData[][];
private int					totPages			= 1;	// total num. of pages in document
private int					sheetsPerSign[];			// how many sheets each signature has

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

public void setFormat(Format formalVal, int maxSheetsPerSignVal, int numOfPages)
{
	format				= formalVal;
	maxSheetsPerSign	= maxSheetsPerSignVal;
	totPages			= numOfPages;
	if (format != Format.booklet)
		maxSheetsPerSign = 1;
	if (maxSheetsPerSign < 1)
		maxSheetsPerSign = 1;
	applyFormat();
}

private void applyFormat()
{
	int		numOfCols, numOfRows;
	
	// set number of rows and columns according to format
	numOfCols = numOfCols();
	numOfRows = numOfRows();
	// compute some helper values
	int		pagesPerSheet	= numOfCols * numOfRows * 2;
	int		numOfSheets		= (totPages + pagesPerSheet-1) / pagesPerSheet;
	int		numOfSigns		= (numOfSheets + maxSheetsPerSign-1) / maxSheetsPerSign;
	int		minSheetsPerSign= numOfSheets / numOfSigns;	// at least as many sheets per signature
	int		extraSheets		= numOfSheets - (numOfSigns * minSheetsPerSign);	// plus as many others
	// determine the number of sheets for each signature, assigning to each signature
	// at least minSheetsPerSign + 1 for the first extraSheets signatures
	sheetsPerSign	= new int[numOfSigns];
	for (int i = 0; i < numOfSigns; i++)
		sheetsPerSign[i] = minSheetsPerSign + (i >= extraSheets ? 0 : 1);
	// max number of pages per signature
	int		pagesPerSign	= (minSheetsPerSign + (extraSheets > 0 ? 1 : 0)) * numOfRows * numOfCols * 2;

	// initialize pageImpoData for each signature according to format
	pageImpoData = new JPDIPageImpoData[numOfSigns][pagesPerSign];
	for (int currSignNo = 0; currSignNo < numOfSigns; currSignNo++)
	{
		int	numOfSrcPages	= numOfSourcePagesPerSignature(currSignNo);
		for (int currPageNo = 0; currPageNo < numOfSrcPages; currPageNo++)
		{
			pageImpoData[currSignNo][currPageNo] = new JPDIPageImpoData();
			// DESTINATION PAGE
			pageImpoData[currSignNo][currPageNo].destPage = (sheetsPerSign[currSignNo] == 1) ?
					// if 1 sheet x sign. (any non-booklet or in-folio booklet):
					//	dest. page either 0 or 1, according to pagenum / 2 is even or odd
					((currPageNo+1) / 2) & 0x0001
					// several sheets x sign. (booklet):
					//	dest. page first increases then decreases
					: Math.min (currPageNo, numOfSrcPages-1 - currPageNo);

			// DESTINATION ROW
			pageImpoData[currSignNo][currPageNo].row = (format == Format.booklet) ?
					// if 1 row x sheet: row always = 0
					0
					// several rows per sheet: use pre-built data
					: prebuiltRowData[format.code()][currPageNo];

			// ROTATION: 0 for even rows, 180 for odd rows
			pageImpoData[currSignNo][currPageNo].rotation =
					(pageImpoData[currSignNo][currPageNo].row & 1) != 0 ? 180 : 0;

			// DESTINATION COLUMN
			pageImpoData[currSignNo][currPageNo].col = (format == Format.booklet) ?
					// column alternates 1 & 0
					1 - (currPageNo & 1)
					// single sheets per sign.: use pre-built data
					: prebuiltColData[format.code()][currPageNo];
		}
	}
}

public int	numOfSourcePagesPerSignature(int signNo)
{
	return sheetsPerSign[signNo] * numOfCols() * numOfRows() * 2;
}

public int numOfDestPagesPerSignature(int signNo)
{
	return sheetsPerSign[signNo] * 2;
}

/******************
	Getters for individual page destination parameters
*******************/

public int pageDestPage(int srcPageNo, int signNo)
{
	if (srcPageNo < 0 || srcPageNo > pageImpoData[signNo].length-1)
		srcPageNo = 0;
	return pageImpoData[signNo][srcPageNo].destPage;
}
public int pageDestRow(int srcPageNo, int signNo)
{
	if (srcPageNo < 0 || srcPageNo > pageImpoData[signNo].length-1)
		srcPageNo = 0;
	return pageImpoData[signNo][srcPageNo].row;
}
public int pageDestCol(int srcPageNo, int signNo)
{
	if (srcPageNo < 0 || srcPageNo > pageImpoData[signNo].length-1)
		srcPageNo = 0;
	return pageImpoData[signNo][srcPageNo].col;
}
// Rotation: if page is upside down, add an extra col and row of offset,
// to compensate the rotation around the bottom left corner
public double pageDestOffsetX(int srcPageNo, int signNo, double srcPageWidth)
{
	if (srcPageNo < 0 || srcPageNo > pageImpoData[signNo].length-1)
		srcPageNo = 0;
	return srcPageWidth * pageImpoData[signNo][srcPageNo].col
			+ (pageImpoData[signNo][srcPageNo].rotation > 0 ? srcPageWidth : 0.0);
}
public double pageDestOffsetY(int srcPageNo, int signNo, double srcPageHeight)
{
	if (srcPageNo < 0 || srcPageNo > pageImpoData[signNo].length-1)
		srcPageNo = 0;
	return srcPageHeight * pageImpoData[signNo][srcPageNo].row
			+ (pageImpoData[signNo][srcPageNo].rotation > 0 ? srcPageHeight : 0.0);
}
public int pageDestRotation(int srcPageNo, int signNo)
{
	if (srcPageNo < 0 || srcPageNo > pageImpoData[signNo].length-1)
		srcPageNo = 0;
	return pageImpoData[signNo][srcPageNo].rotation;
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
	default:				// any other string defaults to "booklet"
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
