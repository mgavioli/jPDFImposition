/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	JPDIDocument.java - Sub-class of PDDocument, for creating an imposed document from an original PDDocument

	Created by : Maurizio M. Gavioli 2014-12-17
*****************************/

package com.vistamaresoft.jpdfi;
//import com.vistamaresoft.jpdfi.JPDImposition;

import java.io.IOException;
import java.util.HashMap;
//import java.util.Iterator;


import de.intarsys.pdf.cds.CDSRectangle;
import de.intarsys.pdf.content.CSContent;
import de.intarsys.pdf.content.common.CSCreator;
import de.intarsys.pdf.cos.COSName;
//import de.intarsys.pdf.cos.COSDocument;
import de.intarsys.pdf.cos.COSObject;
import de.intarsys.pdf.cos.COSStream;
import de.intarsys.pdf.parser.COSLoadException;
import de.intarsys.pdf.pd.PDDocument;
//import de.intarsys.pdf.pd.PDForm;
import de.intarsys.pdf.pd.PDPage;
//import de.intarsys.pdf.pd.PDPageNode;
import de.intarsys.pdf.pd.PDPageTree;
import de.intarsys.pdf.pd.PDResources;
import de.intarsys.tools.locator.FileLocator;

/******************
	CLASS JPDIDocument
*******************/

public class JPDIDocument extends Object
{
	private JPDImposition	impo;
	private PDDocument		srcDoc;
	private PDDocument		dstDoc;

	/******************
		C'tors
	*******************/

	public JPDIDocument()
	{
		srcDoc = null;
		dstDoc = PDDocument.createNew();
	}

	public JPDIDocument(String filename)
	{
		setSourceFileName(filename);
		dstDoc = PDDocument.createNew();
		impo = new JPDImposition();
	}

	/******************
		Apply the imposition
	*******************
	Fills the document with data from sourceDoc according to impo. */

	public boolean impose()
	{
		if (srcDoc == null)
			return false;
		PDPageTree	pageTree = srcDoc.getPageTree();

		// compute some helper values
		int		pagesPerSheet	= impo.numOfCols() * impo.numOfRows() * 2;
		int		numOfSrcPages	= pageTree.getCount();
		int		numOfSheets		= (numOfSrcPages + pagesPerSheet-1) / pagesPerSheet;
		int		numOfSigns		= (numOfSheets + impo.maxSheetsPerSignature()-1)
				/ impo.maxSheetsPerSignature();
		int		maxSheetsPerSign= Math.min(numOfSheets, impo.maxSheetsPerSignature());
		// determine the number of sheets for each signature
		int		missingSheetsInLastSign	= (maxSheetsPerSign - (numOfSheets % maxSheetsPerSign)) % maxSheetsPerSign;
		int[]	sheetsPerSign	= new int[numOfSigns];
		for (int i = 0; i < numOfSigns; i++)
			sheetsPerSign[i] = maxSheetsPerSign - (missingSheetsInLastSign > i ? 1 : 0);

//		JPDImpoData	impoData	= new JPDImpoData(impo.format(), maxSheetsPerSign);
		PDPage		currSrcPage	= pageTree.getFirstPage();
		int			currSign	= 0;
		HashMap		resMap		= new HashMap();
		while (currSrcPage != null)
		{
			int		numOfDestPages = sheetsPerSign[currSign]*2;
			// get destination crop box from source page crop box
			CDSRectangle box	= currSrcPage.getMediaBox().copy().normalize();
			float srcPageHeight	= box.getHeight();
			float srcPageWidth	= box.getWidth();
			box.setHeight(srcPageHeight * impo.numOfRows());
			box.setWidth (srcPageWidth  * impo.numOfCols());
			// create new, empty, destination pages
			PDPage		destPage[]		= new PDPage[numOfDestPages];
			CSContent	destContent[]	= new CSContent[numOfDestPages];
			CSCreator	destCreator[]	= new CSCreator[numOfDestPages];
			for (int pageNo = 0; pageNo < sheetsPerSign[currSign]*2; pageNo++)
			{
				destPage[pageNo]	= (PDPage) PDPage.META.createNew();
				destPage[pageNo].setMediaBox(box.copy());
				destContent[pageNo]	= CSContent.createNew();
				destCreator[pageNo]	= CSCreator.createFromContent(destContent[pageNo], currSrcPage);
			}
			// insert source pages, each at its proper destination
			for (int currSignPageNo = 0;
				currSrcPage != null && currSignPageNo < sheetsPerSign[currSign]*pagesPerSheet;
					currSignPageNo++)
			{
				int destPageNo = impo.pageDestPage(currSignPageNo);
				// set page transformation into destination place
				double	rot		= impo.pageDestRotation(currSignPageNo) * Math.PI / 180.0;
				double	cosRot	= Math.cos(rot);
				double	sinRot	= Math.sin(rot);
				// if page is upside down, add an extra col and row of offset, to compensate the rotation around the bottom left corner
				double	offsetX	= srcPageWidth  * impo.pageDestCol(currSignPageNo) + (rot > 0 ? srcPageWidth  : 0.0);
				double	offsetY	= srcPageHeight * impo.pageDestRow(currSignPageNo) + (rot > 0 ? srcPageHeight : 0.0);
				double	scaleX	= 1.0;
				double	scaleY	= 1.0;
				destCreator[destPageNo].saveState();
				destCreator[destPageNo].transform(
					(float)(scaleX*cosRot), (float)(scaleX*sinRot),
					-(float)(scaleY*sinRot), (float)(scaleY*cosRot),
					(float)offsetX, (float)offsetY);
				// copy source page contents and resources
				destCreator[destPageNo].copy(currSrcPage.getContentStream());
				if (currSrcPage.getResources() != null) {
/* An unfinished attempt
					COSObject	cosRes	= currSrcPage.getResources().cosGetObject();
					PDResources	pdRes	= (PDResources)PDResources.META.createNew();
					Iterator	iter	= cosRes.iterator();
					while(iter.hasNext())
					{
						Object		key	= iter.next();
						COSObject	res	= (COSObject)resMap.get(key);
						COSObject	resCopy	= res.copyDeep(resMap);
					}
*/
					COSObject	cosRes	= currSrcPage.getResources().cosGetObject().copyDeep(resMap);
					PDResources	pdRes	= (PDResources)PDResources.META.createFromCos(cosRes);
					destPage[destPageNo].setResources(pdRes);
				}
				// OR (this does not seem to copy anything to destination pages!!)
/*
				PDForm form = createForm(currSrcPage);
				destCreator[destPageNo].doXObject(null, form);
*/
				destCreator[destPageNo].restoreState();
				currSrcPage = currSrcPage.getNextPage();
			}
			// add dest. pages to dest. document
			for (int destPageNo = 0; destPageNo < sheetsPerSign[currSign]*2; destPageNo++)
			{
				destCreator[destPageNo].close();
				COSStream pageStream = destContent[destPageNo].createStream();
				pageStream.addFilter(COSName.constant("FlateDecode"));
				destPage[destPageNo].cosAddContents(pageStream);
				dstDoc.addPageNode(destPage[destPageNo]);
				destCreator[destPageNo] = null;		// a bit of paranoia!
				destContent[destPageNo] = null;
				destPage[destPageNo] = null;
			}
			currSign++;
		}
		return true;
	}

	/******************
		Save the destination document
	*******************/

	protected boolean save(String filename) /*throws IOException*/
	{
		FileLocator locator = new FileLocator(filename);
		try {
			dstDoc.save(locator, null);
			dstDoc.close();
		}
		catch (IOException e) {
			System.err.println("Caught IOException: " + e.getMessage());
			return false;
		}
		return true;
	}

	/******************
		Getters
	*******************/

	public JPDImposition.Format	format()	{ return impo.format();	}

	/******************
		Setters
	*******************/

	public void	setSourceDoc(PDDocument doc)	{ srcDoc = doc;	}

	public void setSourceFileName(String filename) /*throws IOException, COSLoadException*/
	{
		if (srcDoc != null)
		{
			try {
				srcDoc.close();
				srcDoc = null;
			}
			catch (IOException e) {
				System.err.println("Error closing document from file : " + srcDoc.getLocator().getFullName());
				System.exit(1);
			}
		}
		FileLocator	locator	= new FileLocator(filename);
		try {
			srcDoc = PDDocument.createFromLocator(locator);
		}
		catch (IOException e) {
			System.err.println("Error opening file : " + filename);
			System.exit(1);
		}
		catch (COSLoadException e) {
				System.err.println("Error parsing file : " + filename);
				System.exit(1);
		}
	}

	public void setFormat(String format, String sheetsPerSign)
	{
		impo.setFormat(format, sheetsPerSign);
	}
/*
	public void setMaxSheetsPerSignature(int val)
	{
		impo.setMaxSheetsPerSignature(val);
	}
*/
	/******************
		Create a PDForm out of a PDPage
	*******************/
/*
	protected PDForm createForm(PDPage page) {
		PDForm form = (PDForm) PDForm.META.createNew();
		if (page.getResources() != null) {
			COSObject cosRes	= page.getResources().cosGetObject().copyDeep();
			PDResources pdRes	= (PDResources) PDResources.META.createFromCos(cosRes);
			form.setResources(pdRes);
		}
		CSContent content = page.getContentStream();
		form.setBytes(content.toByteArray());
		form.setBoundingBox(page.getCropBox().copy());
		return form;
	}
*/
}
