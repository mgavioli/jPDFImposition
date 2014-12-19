/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	JPDImposition.java - Describes an imposition

	Created by : Maurizio M. Gavioli 2014-12-17
*****************************/

package com.vistamaresoft.jpdfi;

//import com.vistamaresoft.jpdfi.JPDIImpoData;
//import com.vistamaresoft.jpdfi.JPDIImpoData.Format;

/******************
	CLASS JPDImposition
*******************/

public class JPDImposition
{
	private JPDImpoData.Format	format		= JPDImpoData.Format.booklet;
	private int		maxSheetsPerSignature	= 5;

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

	public JPDImpoData.Format	format()			{ return format;				}
	public int numOfCols()
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
	public int maxSheetsPerSignature()				{ return maxSheetsPerSignature;	}

	public void setMaxSheetsPerSignature(int val)
	{
		maxSheetsPerSignature = val;
		if (maxSheetsPerSignature != 1)
			format = JPDImpoData.Format.booklet;
	}

	public void setFormat(JPDImpoData.Format val)
	{
		format = val;
		if (format != JPDImpoData.Format.booklet)
			maxSheetsPerSignature = 1;
	}

	public void setFormat(String val)
	{
		format = JPDImpoData.formatStringToVal(val);
		if (format != JPDImpoData.Format.booklet)
			maxSheetsPerSignature = 1;
		else				// if format was booklet, attempt to see if it was expressed as a number of sheets
		{
			int num = Integer.parseInt(val);
			if (num > 0)
				maxSheetsPerSignature = num;
		}
	}
}
