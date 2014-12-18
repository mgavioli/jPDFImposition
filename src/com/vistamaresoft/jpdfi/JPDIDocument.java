/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	JPDIDocument.java - Sub-class of PDDocument, for creating an imposed document from an original PDDocument

	Created by : Maurizio M. Gavioli 2014-12-17
*****************************/

package com.vistamaresoft.jpdfi;
//import com.vistamaresoft.jpdfi.JPDImposition;

import java.io.IOException;

import de.intarsys.pdf.cds.CDSRectangle;
import de.intarsys.pdf.content.CSContent;
import de.intarsys.pdf.content.common.CSCreator;
//import de.intarsys.pdf.cos.COSDocument;
import de.intarsys.pdf.cos.COSObject;
import de.intarsys.pdf.parser.COSLoadException;
import de.intarsys.pdf.pd.PDDocument;
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

	public JPDIDocument(PDDocument sourceDoc)
	{
		srcDoc = sourceDoc;
		dstDoc = PDDocument.createNew();
	}

	public JPDIDocument(String filename) throws IOException, COSLoadException
	{
		FileLocator	locator	= new FileLocator(filename);
		srcDoc = PDDocument.createFromLocator(locator);
		dstDoc = PDDocument.createNew();
		impo = new JPDImposition();
	}

	/******************
		Getters / Setters
	*******************/

	public void	setSourceDoc(PDDocument doc)	{ srcDoc = doc;	}

	/******************
		Apply the imposition
	*******************
	Fills the document with data from sourceDoc according to impo. */

	public boolean impose()
	{
		if (srcDoc == null)
			return false;
		return impose(srcDoc.getPageTree());
	}

	/******************
		Recursively apply the imposition
	*******************
	Fills the document with data from sourceDoc according to impo. */

	protected boolean impose(PDPageTree pageTree)
	{
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

		JPDImpoData	impoData	= new JPDImpoData(impo.format(), maxSheetsPerSign);
		PDPage		currSrcPage	= pageTree.getFirstPage();
		int			currSign	= 0;
		while (currSrcPage != null)
		{
			int		numOfDestPages = sheetsPerSign[currSign]*2;
			// get destination crop box from source page crop box
			CDSRectangle box = currSrcPage.getMediaBox().copy().normalize();
			box.setHeight(box.getHeight() * impo.numOfRows());
			box.setWidth(box.getWidth() * impo.numOfCols());
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
				currSrcPage != null && currSignPageNo < sheetsPerSign[currSign]*pagesPerSheet*2;
					currSignPageNo++)
			{
				int destPageNo = impoData.pageDestPage(currSignPageNo);
				// set page transformation into destination place
				destCreator[destPageNo].saveState();
				double	rot		= impoData.pageDestRotation(currSignPageNo); 
				double	cosRot	= Math.cos(rot);
				double	sinRot	= Math.sin(rot);
				double	offsetX	= 72 * impoData.pageDestCol(currSignPageNo);
				double	offsetY	= 72 * impoData.pageDestRow(currSignPageNo);
				double	scaleX	= 1.0;
				double	scaleY	= 1.0;
				destCreator[destPageNo].transform(
					(float)(scaleX*cosRot), (float)(scaleX*sinRot),
					-(float)(scaleY*sinRot), (float)(scaleY*cosRot),
					(float)offsetX, (float)offsetY);
				// copy source page contents and resources
				destCreator[destPageNo].copy(currSrcPage.getContentStream());
				if (currSrcPage.getResources() != null) {
					COSObject cosRes	= currSrcPage.getResources().cosGetObject().copyDeep();
					PDResources pdRes	= (PDResources)PDResources.META.createFromCos(cosRes);
					destPage[destPageNo].setResources(pdRes);
				}
				// OR
//				destCreator[destPageNo].doXObject(null, currSrcPage);
				destCreator[destPageNo].restoreState();
				currSrcPage = currSrcPage.getNextPage();
			}
			// add dest. pages to dest. document
			for (int destPageNo = 0; destPageNo < sheetsPerSign[currSign]*2; destPageNo++)
			{
				destCreator[destPageNo].close();
				destPage[destPageNo].cosAddContents(destContent[destPageNo].createStream());
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
}
