/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	JPDImposition.java - Describes an imposition

	Created by : Maurizio M. Gavioli 2014-12-17

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

import java.util.ArrayList;
import java.util.TreeSet;

/******************
	CLASS JPDImposition
*******************/

public class JPDImposition
{
// PUBLIC DEFINITIONS

public static final int		DEFAULT_SHEETS_PER_SIGN	= 5;
public static final double	MM2PDF					= 72 / 25.4;	// to convert mm to PDF default units (1/72 inch)
public static final int		OUT_OF_SEQUENCE_PAGE	= -1;
public static final int		NO_PAGE					= -2;

public enum Format
{
	in4h	(0),	// in 4° format, first fold is horizontal
	in4v	(1),	// in 4° format, first fold is vertical
	in8h	(2),	// in 8° format, first fold is horizontal
	in8v	(3),	// in 8° format, first fold is vertical
	in16h	(4),	// in 16° format, first fold is horizontal
	in16v	(5),	// in 16° format, first fold is vertical
	booklet	(6),	// booklet format (multiple sheets bounded together)
	none	(7);	// no imposition, used for verbatim concatenation

	private final int formatCode;
	Format(int formatCode)	{ this.formatCode = formatCode;	}
	public int code()		{ return this.formatCode; }
}

// PRIVATE DEFINITIONS

private static final double	FOLDOUT_XOFFSET			= (7*MM2PDF);	// how much to shift the internal pages of a fold-out

private class JPDIPageImpoData implements Cloneable
{
	int	destPage;		// in which page of the signature the page shall end up (0-based)
	int	row;			// in which row of that page (0-based)
	int	col;			// in which column of that page (0-based)
	int	rotation;		// with which rotation (in degrees)
	double xOffset;		// the horizontal offset (in PDF units)
	double yOffset;		// the vertical offset (in PDF units)

	@Override
	protected Object clone() throws CloneNotSupportedException
	{
		return super.clone();
	}
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

// FIELDS

private Format	format				= Format.booklet;
private int		formatSubParam		= 0;
private int		maxSheetsPerSign	= DEFAULT_SHEETS_PER_SIGN;	// max num. of sheets per signature
// for each source page of each signature, define where and how to place it into the destination
private ArrayList<ArrayList<JPDIPageImpoData>>	pageImpoData;
//private int		totPages			= 1;	// total num. of pages in document
private ArrayList<Integer>	sheetsPerSign;		// how many sheets each signature has

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

public Format	format()			{ return format;	}
public int 		numOfCols()
{
	switch (format)
	{
	case none:
		return 1;
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

public void setFormat(Format format, int formatSubParam, int maxSheetsPerSign,
		int numOfPages, TreeSet<Integer>signBreakList, TreeSet<Integer>foldOutList)
		throws CloneNotSupportedException
{
	this.format				= format;
	this.formatSubParam		= formatSubParam;
	this.maxSheetsPerSign	= maxSheetsPerSign;
	pageImpoData			= new ArrayList<ArrayList<JPDIPageImpoData>>();
	sheetsPerSign			= new ArrayList<Integer>();
//	totPages				= numOfPages;
	if (format != Format.booklet)
		maxSheetsPerSign = 1;
	if (maxSheetsPerSign < 1)
		maxSheetsPerSign = 1;
	// make sure there is a signature break after the last page
	if (signBreakList.size() == 0 || !signBreakList.contains(numOfPages))
		signBreakList.add(numOfPages);
	// iterate on signature breaks, creating a sequence of impositions
	int currSignNo	= 0;
	int	fromPage 	= 0;
	for (Integer toPage : signBreakList)
	{
		currSignNo = applyFormat(currSignNo, fromPage, toPage, foldOutList);
		fromPage = toPage;
	}
}

private int applyFormat(int currSignNo, int fromPage, int toPage, TreeSet<Integer>foldOutList)
		throws CloneNotSupportedException
{
	int		docPageNo	= fromPage;				// the current document page no. (0-based)
	formatSetup(fromPage, toPage, foldOutList);

	// initialize pageImpoData for each page of each signature according to format
	while (docPageNo < toPage)
	{
		int maxDestPage		= 0;
		int	numOfSrcPages	= sheetsPerSign.get(currSignNo) * numOfCols()* numOfRows() * 2;
		ArrayList<JPDIPageImpoData>	signImpoData = new ArrayList<JPDIPageImpoData>(numOfSrcPages);
		pageImpoData.add(signImpoData);
		for (int currPageNo = 0; docPageNo < toPage && currPageNo < numOfSrcPages; currPageNo++)
		{
			JPDIPageImpoData pid = new JPDIPageImpoData();
			pid.xOffset = pid.yOffset = 0;
			signImpoData.add(pid);

			// BOOKLET FOLD-OUT SPECIAL CASE
			if (format == Format.booklet && foldOutList.contains(docPageNo))
			{
				// we deal here with 4 different pages:
				// 1st: the front page of the leaf the fold-out is attached to ('base leaf', previous page, pid1)
				// 2nd: the fold-out front page (current page, pid)
				// 3rd: the fold-out back page (pid3)
				// 4th: the base leaf back page (pid4)
				// 2nd and 3rd (fold-out) are 'shifted' in the position of 1st and 4th (base page) resp.
				// 1st and 4th (base page) are moved to the 'other' column of the same page
				// the 3rd and 4th pages are yet to be seen, but are managed here anyway
				int					currArrSize			= signImpoData.size();
				JPDIPageImpoData	pid1				= signImpoData.get(currArrSize-2);
				boolean				hasSingleOpposite	= true;		// by default, there is an opposite single page
				// if pid1 is an OUT_OF_SEQUENCE_PAGE, we are dealing with two fold-outs glued together
				if (pid1.destPage == OUT_OF_SEQUENCE_PAGE)
				{
					// 1st page is first of a new dest. page
					pid1.col		= 0;				// first column
					pid1.destPage	= (++maxDestPage);	// of a new page
					// TODO : .row contains the glue-to page; it would be useful
					// to keep this info somewhere; possibly it is worth adding
					// a JPDIPageImpoData field for this, instead of recycling .row
					pid1.row		= 0;				// booklet only has 1 row
					hasSingleOpposite	= false;		// opposite is not a single page
					// 2nd page is second of the new dest. page
					pid.col			= 1;
					pid.destPage	= pid1.destPage;
					pid.rotation	= pid1.rotation;
					pid.row			= pid1.row;
				}
				else				// no double fold-out
				{
					pid.col			= pid1.col;			// move 2nd page in the place of 1st
					pid.destPage	= pid1.destPage;
					pid.rotation	= pid1.rotation;
					pid.row			= pid1.row;
					pid1.col		= 1 - pid.col;		// move 1st page in the 'other' column
				}
				pid1.xOffset	= FOLDOUT_XOFFSET;		// shift 1st page slightly to the right
				// 'next page' is +1 while 'going up' the signature (front leaves) and 1st page is on even dest. page
				// and -1 while 'going down' (back leaves) and 1st page is on odd dest. page
				int	nextPageOffset	= (pid.destPage & 1) > 0 ? -1 : +1;
				// if 'going down', the single leaf opposite to the fold-out sheet has already been seen:
				// locate its two pages and move them out of sequence
				// (unless we are dealing with a double fold-out)
				if (nextPageOffset == -1 && hasSingleOpposite)
				{
					signImpoData.get(pid1.destPage).destPage	= OUT_OF_SEQUENCE_PAGE;
					signImpoData.get(pid1.destPage).row			= docPageNo - 1;	// glue to pid1
					signImpoData.get(pid1.destPage-1).destPage	= OUT_OF_SEQUENCE_PAGE;
				}
//				currPageNo++;		// NO: fold-out pages do not count for imposition
				docPageNo++;
				if (pid.destPage > maxDestPage)
					maxDestPage = pid.destPage;
				if (docPageNo < toPage)
				{
					// fold-out back page (3rd page) is in the same dest. page position as 1st page
					JPDIPageImpoData pid3 = (JPDIPageImpoData)pid1.clone();
					signImpoData.add(pid3);
					pid3.destPage	= pid1.destPage + nextPageOffset;
					// and on the other side of 2nd page: set symmetrical x offset
					pid3.xOffset	= -pid.xOffset;
//					currPageNo++;		// NO: fold-out pages do not count for imposition
					docPageNo++;
					if (pid3.destPage > maxDestPage)
						maxDestPage = pid3.destPage;
					if (docPageNo < toPage)
					{
						// back page of base leaf (4th page) is in the same dest. page position as 2nd page
						JPDIPageImpoData pid4 = (JPDIPageImpoData)pid.clone();
						signImpoData.add(pid4);
						pid4.destPage	= pid.destPage + nextPageOffset;
						// and on the other side of 1st page: set symmetrical x offset
						pid4.xOffset	= -pid1.xOffset;
						docPageNo++;
					}
				}
				continue;
			}

			// DESTINATION PAGE
			pid.destPage = (format == Format.booklet) ?
					// dest. page first increases then decreases
					Math.min (currPageNo+formatSubParam, numOfSrcPages-1 - currPageNo-formatSubParam)
					// 1 sheet x sign. (any non-booklet):
					//	dest. page either 0 or 1, according to pagenum / 2 is even or odd
					: ((currPageNo+1) / 2) & 0x0001;
			if (pid.destPage < 0)		// may happen with booklet + -1 shift, in which page 0
				pid.destPage = 0;		// ends up in destPage -1, but it should be 0
			// DESTINATION ROW
			pid.row = (format == Format.booklet) ?
					// if 1 row x sheet: row always = 0
					0
					// several rows per sheet: use pre-built data
					: prebuiltRowData[format.code()][currPageNo];
			// ROTATION: 0 for even rows, 180 for odd rows
			pid.rotation = (pid.row & 1) != 0 ? 180 : 0;
			// DESTINATION COLUMN
			pid.col = (format == Format.booklet) ?
					// column alternates 1 & 0
					1 - (currPageNo+formatSubParam & 1)
					// single sheets per sign.: use pre-built data
					: prebuiltColData[format.code()][currPageNo];

			// BOOKLET FOLD-OUT SPECIAL CASE:
			// is this a page of the leaf to be glued to a fold-out?
			if (format == Format.booklet)
				// if the place of this page is already taken by a previous page
				// this is the page opposite to a fold-out: mark as out-of-sequence
				for (int i = 0; i <= signImpoData.size()-2; i++)
					if (signImpoData.get(i).destPage == pid.destPage
							&& signImpoData.get(i).col == pid.col)
					{
						// if this is the back page (destination page is odd), note page to glue to
						if ((pid.destPage & 1) == 1)
							pid.row = docPageNo - signImpoData.size() + i + 1;
						pid.destPage = OUT_OF_SEQUENCE_PAGE;
						break;
					}

			if (pid.destPage > maxDestPage)
				maxDestPage = pid.destPage;
			docPageNo++;
		}
		// set actual number of signature sheets
		sheetsPerSign.set(currSignNo, ((maxDestPage + 2) & 0xFFFE) / 2 );
		currSignNo++;
	}
	return currSignNo;
}

/******************
	Format setup
*******************

Sets arrays up for application of imposition. */

private void formatSetup(int fromPage, int toPage, TreeSet<Integer>foldOutList)
{
	int		numOfCols, numOfRows;

	// set number of rows and columns according to format
	numOfCols = numOfCols();
	numOfRows = numOfRows();
	// compute some helper values
	int	pagesPerSheet	= numOfCols * numOfRows * 2;
	int	numOfSheetPages	= toPage - fromPage;
	// if booklet, exclude fold-out pages from pages usable for sheets
	if (format == Format.booklet)
		for (Integer foldOut : foldOutList)
			if (foldOut >= fromPage && foldOut < toPage)
			{
				if (foldOut < toPage-1)		// if fold-out is not the last page
					numOfSheetPages -= 2;	//	it takes 2 pages away from reckon
				else						// if it the last page
					numOfSheetPages -= 1;	//	it takes away only itself
			}
	// round up number of sheets and number of signatures
	int	numOfSheets		= (numOfSheetPages + pagesPerSheet-1) / pagesPerSheet;
	int	numOfSigns		= (numOfSheets + maxSheetsPerSign-1) / maxSheetsPerSign;
	// signature balancing: at least as many sheets per signature:
	int	minSheetsPerSign= numOfSheets / numOfSigns;
	// and as many other sheets to distribute across all signatures (of course, extraSheets < numOfSigns)
	int	extraSheets		= numOfSheets - (numOfSigns * minSheetsPerSign);
	// determine the number of sheets for each signature, assigning to each signature
	// at least minSheetsPerSign + 1 for the first extraSheets signatures
	for (int i = 0; i < numOfSigns; i++)
		sheetsPerSign.add(minSheetsPerSign + (i >= extraSheets ? 0 : 1) );
}

/******************
	Getters for numbers of source / destination pages
*******************/

public int	numOfSourcePagesPerSignature(int signNo)
{
	if (signNo < 0 || signNo > pageImpoData.size() - 1)
		return 0;
	return pageImpoData.get(signNo).size();
}

public int numOfDestPagesPerSignature(int signNo)
{
	if (signNo < 0 || signNo > sheetsPerSign.size() - 1)
		return 0;
	return sheetsPerSign.get(signNo) * 2;
}

/******************
	Getters for individual page destination parameters
*******************/

public int pageDestPage(int srcPageNo, int signNo)
{
	if (srcPageNo < 0 || srcPageNo > pageImpoData.get(signNo).size()-1)
		return NO_PAGE;
	return pageImpoData.get(signNo).get(srcPageNo).destPage;
}
public int pageDestRow(int srcPageNo, int signNo)
{
	if (srcPageNo < 0 || srcPageNo > pageImpoData.get(signNo).size()-1)
		srcPageNo = 0;
	return pageImpoData.get(signNo).get(srcPageNo).row;
}
public int pageDestCol(int srcPageNo, int signNo)
{
	if (srcPageNo < 0 || srcPageNo > pageImpoData.get(signNo).size()-1)
		srcPageNo = 0;
	return pageImpoData.get(signNo).get(srcPageNo).col;
}
// Rotation: if page is upside down, add an extra col and row of offset,
// to compensate the rotation around the bottom left corner
public double pageDestOffsetX(int srcPageNo, int signNo, double srcPageWidth)
{
	if (srcPageNo < 0 || srcPageNo > pageImpoData.get(signNo).size()-1)
		srcPageNo = 0;
	JPDIPageImpoData pid = pageImpoData.get(signNo).get(srcPageNo);
	return srcPageWidth * pid.col + (pid.rotation > 0 ? srcPageWidth : 0.0) + pid.xOffset;
}
public double pageDestOffsetY(int srcPageNo, int signNo, double srcPageHeight)
{
	if (srcPageNo < 0 || srcPageNo > pageImpoData.get(signNo).size()-1)
		srcPageNo = 0;
	JPDIPageImpoData pid = pageImpoData.get(signNo).get(srcPageNo);
	return srcPageHeight * pid.row + (pid.rotation > 0 ? srcPageHeight : 0.0) + pid.yOffset;
}
public int pageDestRotation(int srcPageNo, int signNo)
{
	if (srcPageNo < 0 || srcPageNo > pageImpoData.get(signNo).size()-1)
		srcPageNo = 0;
	return pageImpoData.get(signNo).get(srcPageNo).rotation;
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
	case none:
		return "none";
	default:				// any other value defaults to "booklet"
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
	else if (format.equals("none"))
		return Format.none;
	return Format.booklet;	// any other string defaults to "booklet"
}
}
