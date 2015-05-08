/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	JPDIDocument.java - Creates an imposed PDFt from source PDF's

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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
//import java.util.Observable;
import java.util.TreeSet;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import de.intarsys.pdf.cds.CDSRectangle;
import de.intarsys.pdf.content.CSContent;
import de.intarsys.pdf.content.common.CSCreator;
import de.intarsys.pdf.cos.COSArray;
import de.intarsys.pdf.cos.COSCatalog;
import de.intarsys.pdf.cos.COSCompositeObject;
import de.intarsys.pdf.cos.COSDocument;
import de.intarsys.pdf.cos.COSDocumentElement;
import de.intarsys.pdf.cos.COSDictionary;
import de.intarsys.pdf.cos.COSIndirectObject;
import de.intarsys.pdf.cos.COSName;
import de.intarsys.pdf.cos.COSNull;
import de.intarsys.pdf.cos.COSObject;
import de.intarsys.pdf.cos.COSStream;
import de.intarsys.pdf.encoding.WinAnsiEncoding;
import de.intarsys.pdf.font.PDFont;
import de.intarsys.pdf.font.PDFontType1;
import de.intarsys.pdf.parser.COSLoadException;
import de.intarsys.pdf.pd.PDDocument;
import de.intarsys.pdf.pd.PDPage;
import de.intarsys.pdf.pd.PDPageTree;
import de.intarsys.pdf.pd.PDResources;
import de.intarsys.pdf.st.STDocType;
import de.intarsys.tools.locator.FileLocator;

/******************
	CLASS JPDIDocument
*******************/

public class JPDIDocument /*extends Object*/
{
// PRIVATE DEFINITIONS

private static final int		FRONT_PAGE		= 0;			// for indices into pageOffsetX/Y
private static final int		BACK_PAGE		= 1;
private static final int		INVALID_PARAM	= -1000000;		// used as rejected parameter value
//Turn the use of JDPIResourceManager on instead of an older way of merging
//which had proven unreliable and will be removed. Leave on 'true'.
public static final boolean		useMerger		= true;
public static final boolean		dontOpenAllDocs	= false;

// Data about a source document

private class JPDISourceDoc
{
			String		fileName;			// the document file name (hopefully as an absolute path)
	private	int			fromPage, toPage;	// this document is used from page fromPage to page toPage
	private	int			numOfPages;			// number of pages this document will provide
	private	int			pageNoOffset;		// the offset from page number to page sequence index
			PDDocument	doc;				// the document itself
/*
	public int	fromPage()		{ return fromPage;		}
	public int	numOfPages()	{ return numOfPages;	}
	public int	pageNoOffset()	{ return pageNoOffset;	}
	public int	toPage()		{ return toPage;		}
*/
	public void	setFromPage(int val)
	{
		int newVal	= val + pageNoOffset - 1;
		int	delta	= newVal - fromPage;
		fromPage	= newVal;
		numOfPages	-= delta;
	}
	public void	setToPage(int val)
	{
		int newVal	= val + pageNoOffset - 1;
		int	delta	= newVal - toPage;
		toPage		= newVal;
		numOfPages	+= delta;
	}
}

// Data about the current source status

private class JPDISourceStatus /*extends Observable*/
{
	private boolean		append;			// whether we are in append mode or not
	private	PDDocument	currDoc;		// the current source document
	private	int			currDocNo;		// the index of the current source document into the srcDocs array
	private int			currDocPageNo;	// the no. (0-based) of the current page in the current document 
	private	PDPage		currPage;		// the current source page
	private	ArrayList<JPDISourceDoc>	srcDocs;		// the various source documents
	private ArrayList<JPDISourceDoc>	appendDocs;		// the documents to append as they are

	public void init()
	{
		append			= false;		// start in non-append mode
		currDocNo		= -1;			// before first document
		currDocPageNo	= -1;			// before first page
		currDoc			= null;
		currPage		= null;
		if (srcDocs == null)
			srcDocs		= new ArrayList<JPDISourceDoc>();
		else
			srcDocs.clear();
		if (appendDocs == null)
			appendDocs	= new ArrayList<JPDISourceDoc>();
		else
			appendDocs.clear();
	}

	/******************
		Getters / Setters
	*******************/

	public PDDocument currDoc()		{ return currDoc;	}

	public boolean hasAppend()
	{
		return appendDocs.size() > 0;
	}

	public String inputFileNames()
	{
		String fileNames = "";
		for (JPDISourceDoc doc : srcDocs)
			fileNames += doc.fileName + "\n";
		for (JPDISourceDoc doc : appendDocs)
			fileNames += doc.fileName + "\n";
		return fileNames;
	}

	public PDPage nextPage()
	{
		ArrayList<JPDISourceDoc> docList = append ? appendDocs : srcDocs;

		// if within some document and we reached its end, discard current page
		if (currDocNo >= 0 && currDocPageNo >= docList.get(currDocNo).toPage)
			currPage = null;
		// if we have a current page, get the next
		if (currPage != null)
			currPage = currPage.getNextPage();
		// if no current page (either no doc at all or current doc ended or its toPage was reached)
		if (currPage == null)
		{
			// get next doc, if any
			if (docList != null && currDocNo < docList.size()-1)
			{
if (dontOpenAllDocs)
	closeSrcDoc(currDoc);
				currDocNo++;
				JPDISourceDoc	srcDoc	= docList.get(currDocNo);
if (dontOpenAllDocs)
	currDoc = openSrcDoc(docList.get(currDocNo).fileName);
else
	currDoc = srcDoc.doc;
//				setChanged();
				if (currDoc != null)
				{
					PDPageTree	pageTree 	= currDoc.getPageTree();
					currPage				= pageTree.getFirstPage();
					if (srcDoc.fromPage > 0)
						currPage = currPage.getPageAt(srcDoc.fromPage);
					currDocPageNo			= srcDoc.fromPage;
				}
//				notifyObservers(JPDIMsgs.MSG_NEW_DOC);
			}
		}
		else
			currDocPageNo++;
		return currPage;
	}

	public int pageNoOffset(int pageNo)
	{
		if (srcDocs == null || srcDocs.size() == 0)
			return 0;
		for (JPDISourceDoc doc : srcDocs)
		{
			if (pageNo < doc.numOfPages)
				return doc.pageNoOffset;
			pageNo -= doc.numOfPages;
		}
		return 0;
	}

	public PDPage startAppend()
	{
		// if already in append mode or no doc to append, fail
		if (append || appendDocs.size() < 1)
			return null;
		append = true;
		currPage = null;
		currDocNo = -1;
		return nextPage();
	}

	public int totPages()
	{
		if (srcDocs == null || srcDocs.size() == 0)
			return 0;
		int	totPages = 0;
		for (JPDISourceDoc doc : srcDocs)
			totPages += doc.numOfPages;
		return totPages;
	}

	/******************
		ADD A NEW SOURCE DOCUMENT
	*******************/

	public JPDISourceDoc addSrcDoc(String fileName)
	{
		PDDocument	doc = null;
		// check this source document already exists
		for (JPDISourceDoc currDoc : srcDocs)
		{
			if (currDoc.fileName.equals(fileName))
			{
				doc = currDoc.doc;		// found!
				break;
			}
		}
		if (doc == null)				// if none found, create a new one
			doc	= openSrcDoc(fileName);
		if (doc == null)				// if still no doc => failure
			return null;
		PDPageTree	pageTree 	= doc.getPageTree();
		int			numOfPages	= pageTree.getCount();
if (dontOpenAllDocs)
	if (!closeSrcDoc(doc))
		return null;
		// create a new JPDISourceDoc and add it to the list
		JPDISourceDoc	srcDoc	= new JPDISourceDoc();
		srcDoc.fileName			= fileName;
if (!dontOpenAllDocs)
	srcDoc.doc				= doc;
		srcDoc.numOfPages		= numOfPages;
		srcDoc.fromPage			= 0;
		srcDoc.toPage			= numOfPages - 1;
		// inherit page num. offset from previous document
		if (srcDocs.size() > 0)
			srcDoc.pageNoOffset	= srcDocs.get(srcDocs.size()-1).pageNoOffset;
		srcDocs.add(srcDoc);
		return srcDoc;
	}
	/******************
		ADD A NEW SOURCE DOCUMENT TO APPEND
	*******************/

	public JPDISourceDoc addAppendDoc(String fileName)
	{
		PDDocument	doc	= openSrcDoc(fileName);
		if (doc == null)
			return null;
		PDPageTree	pageTree 	= doc.getPageTree();
		int			numOfPages	= pageTree.getCount();
if (dontOpenAllDocs)
	if (!closeSrcDoc(doc))
		return null;
		// create a new JPDISourceDoc and add it to the list
		JPDISourceDoc	srcDoc	= new JPDISourceDoc();
		srcDoc.fileName			= fileName;
if (!dontOpenAllDocs)
	srcDoc.doc				= doc;
		srcDoc.numOfPages		= numOfPages;
		srcDoc.fromPage			= 0;
		srcDoc.toPage			= numOfPages - 1;
		// inherit page num. offset from previous document
		if (appendDocs.size() > 0)
			srcDoc.pageNoOffset	= srcDocs.get(appendDocs.size()-1).pageNoOffset;
		appendDocs.add(srcDoc);
		return srcDoc;
	}
}

// FIELDS

private PDDocument				dstDoc;
private TreeSet<Integer>		foldOutList;
private JPDImposition.Format	format;
private int						formatSubParam;
private JPDImposition			impo;
private PDFont					impoFont;
private COSName					impoFontName;
private int						maxSheetsPerSign;
private String					outputFileName;
private double					pageOffsetX[]	= { 0.0, 0.0 };
private double					pageOffsetY[]	= { 0.0, 0.0 };
private double					pageSizeX		= 0.0;
private double					pageSizeY		= 0.0;
private TreeSet<Integer>		signBreakList;
private JPDISourceStatus		srcStatus;

/******************
	C'tors
*******************/

public JPDIDocument()
{
	init();
}

public JPDIDocument(String fileName)
{
	init();
	if (fileName != null)
		srcStatus.addSrcDoc(fileName);
}

private void init()
{
	dstDoc				= null;
	if (foldOutList == null)
		foldOutList		= new TreeSet<Integer>();
	else
		foldOutList.clear();
	if (signBreakList == null)
		signBreakList		= new TreeSet<Integer>();
	else
		signBreakList.clear();
	outputFileName		= null;
	impo				= new JPDImposition();
	impoFont			= null;
	format				= impo.format();
	maxSheetsPerSign	= impo.maxSheetsPerSignature();
	pageOffsetX[FRONT_PAGE]	= pageOffsetX[BACK_PAGE]
			= pageOffsetY[FRONT_PAGE] = pageOffsetY[BACK_PAGE] = 0.0;
	if (srcStatus == null)
		srcStatus		= new JPDISourceStatus();
	srcStatus.init();
}

/******************
	Apply the imposition
*******************
Fills the document with data from sourceDoc according to impo. */

public boolean impose()
{
	// Format.none special case
	if (format == JPDImposition.Format.none)
		return concatenate(null);

	// look for a start page and a start document
	PDPage	currSrcPage	= srcStatus.nextPage();
	if (currSrcPage == null)
		return false;
	PDDocument			srcDoc 		= srcStatus.currDoc();
	if (srcDoc == null)
		return false;
	if (!createDestDocument(srcDoc))
		return false;
	int					currSignNo	= 0;
	JPDIResourceMerger	merger 		= null;
	ArrayList<PDPage>	singlePages	= new ArrayList<PDPage>();
	try {
		impo.setFormat(format, formatSubParam, maxSheetsPerSign, srcStatus.totPages(), signBreakList, foldOutList);
	} catch (CloneNotSupportedException e) {
		System.err.println("Error while processing the format: " + e.getMessage());
		e.printStackTrace();
	}
	HashMap<COSIndirectObject, COSCompositeObject> resMap =
			new HashMap<COSIndirectObject, COSCompositeObject>();

	// for each signature
	while (currSrcPage != null)
	{
		int		numOfDestPages = impo.numOfDestPagesPerSignature(currSignNo);
		if (useMerger)
			if (merger == null)
			{
				merger = new JPDIResourceMerger(/*dstDoc,*/ numOfDestPages);
//				srcStatus.addObserver(merger);
			}
			else
				merger.setNumOfDestPages(numOfDestPages);
		// get destination media box from source page media box
		CDSRectangle destBox	= currSrcPage.getMediaBox().copy().normalize();
		// set dimensions, if given as parameters
		if (pageSizeX > 0.0)
			destBox.setWidth((float)pageSizeX);
		if (pageSizeY > 0.0)
			destBox.setHeight((float)pageSizeY);
		float destPageHeight	= destBox.getHeight();
		float destPageWidth		= destBox.getWidth();
		// enlarge to forme sizes
		destBox.setHeight(destPageHeight * impo.numOfRows());
		destBox.setWidth (destPageWidth  * impo.numOfCols());

		// create arrays to hold destination pages (and their appendages) for the whole signature
		PDPage			destPage[]		= new PDPage[numOfDestPages];
		CSContent		destContent[]	= new CSContent[numOfDestPages];
		CSCreator		destCreator[]	= new CSCreator[numOfDestPages];
		COSDictionary	destResDict[]	= new COSDictionary[numOfDestPages];

		// instantiate new pages for the whole signature
		for (int pageNo = 0; pageNo < numOfDestPages; pageNo++)
		{
			destPage[pageNo]	= (PDPage) PDPage.META.createNew();
			destPage[pageNo].setMediaBox(destBox.copy());
			destContent[pageNo]	= CSContent.createNew();
			destCreator[pageNo]	= CSCreator.createFromContent(destContent[pageNo], currSrcPage);
			destResDict[pageNo]	= PDResources.META.createNew().cosGetDict();
		}

		// iterate on source pages of the whole signature,
		// inserting each at its proper destination in dest. pages
		int		numOfSourcePages = impo.numOfSourcePagesPerSignature(currSignNo);
		for (int currSignPageNo = 0;
			currSrcPage != null && currSignPageNo < numOfSourcePages;
				currSignPageNo++)
		{
			int destPageNo = impo.pageDestPage(currSignPageNo, currSignNo);
			if (destPageNo == JPDImposition.NO_PAGE)
				continue;

			// OUT-OF-SEQUENCE PAGE SPECIAL CASE (typically for page opposite to fold-out)
			if (destPageNo == JPDImposition.OUT_OF_SEQUENCE_PAGE)
			{
				createSinglePage(currSrcPage, impo.pageDestRow(currSignPageNo, currSignNo), singlePages, resMap);
				currSrcPage = srcStatus.nextPage();
				continue;
			}

			// set PAGE TRANSFORMATION into destination place

			CDSRectangle srcBox	= currSrcPage.getMediaBox().copy().normalize();
			double	rot		= impo.pageDestRotation(currSignPageNo, currSignNo) * Math.PI / 180.0;
			double	cosRot	= Math.cos(rot);
			double	sinRot	= Math.sin(rot);
			double	offsetX	= impo.pageDestOffsetX(currSignPageNo, currSignNo, destPageWidth)
					+ (destPageWidth - srcBox.getWidth()) * 0.5 + pageOffsetX[destPageNo & 1];
			double	offsetY	= impo.pageDestOffsetY(currSignPageNo, currSignNo, destPageHeight)
					+ (destPageHeight -srcBox.getHeight())* 0.5 + pageOffsetY[destPageNo & 1];
			double	scaleX	= 1.0;
			double	scaleY	= 1.0;
			destCreator[destPageNo].saveState();
			destCreator[destPageNo].transform(
				(float)(scaleX*cosRot), (float)(scaleX*sinRot),
				-(float)(scaleY*sinRot), (float)(scaleY*cosRot),
				(float)offsetX, (float)offsetY);
			// copy source page contents
			destCreator[destPageNo].copy(currSrcPage.getContentStream());

			// COPY RESOURCES

			if (currSrcPage.getResources() != null)
			{
				if (useMerger)
					merger.merge(destPageNo, currSrcPage);
				else
				{
					COSDictionary					resDict	= currSrcPage.getResources().cosGetDict();
					mergeResources(resDict, destResDict[destPageNo]);
				}
			}
			destCreator[destPageNo].restoreState();
			currSrcPage = srcStatus.nextPage();
		}

		// signature is complete: add dest. pages to dest. document
		for (int destPageNo = 0; destPageNo < numOfDestPages; destPageNo++)
		{
			destCreator[destPageNo].close();
			// add content to dest. page
			COSStream pageStream = destContent[destPageNo].createStream();
			pageStream.addFilter(COSName.constant("FlateDecode"));
			destPage[destPageNo].cosAddContents(pageStream);
			// make a copy of accumulated page resources not yet copied and add to dest. page
			COSObject	cosRes;
			if (useMerger)
				cosRes	= merger.getResources(destPageNo).copyDeep(resMap);
			else
				cosRes	= destResDict[destPageNo].copyDeep(resMap);
			PDResources destPageRes	= (PDResources) PDResources.META.createFromCos(cosRes);
			destPage[destPageNo].setResources(destPageRes);
			// add page to doc and release objects no longer needed
			dstDoc.addPageNode(destPage[destPageNo]);
			destCreator[destPageNo]	= null;		// a bit of paranoia!
			destContent[destPageNo]	= null;
			destPage[destPageNo]	= null;
			destResDict[destPageNo]	= null;
		}
		if (useMerger)
			merger.releaseDestPages();
		currSignNo++;
	}
	// add single pages, if any
	for (int i = 0; i < singlePages.size(); i++)
			dstDoc.addPageNode(singlePages.get(i));
	try {
		srcDoc.close();
	} catch (IOException e) {
		// empty catch: if we cannot close it, just ignore it!
	}

	// add appended documents, if any
	if (srcStatus.hasAppend())
	{
		currSrcPage = srcStatus.startAppend();
		return concatenate(resMap);
	}
	return true;
}

/******************
	CONCATENATE
*******************
Concatenates several source documents into the destination document.
Used for:
* imposition format Format.none (all input documents are concatenated into a single output doc)
* appending unformatted documents to an imposition.

Parameters:	resMap: a Map used to collect page resources; may be null if no map already exists
				(typically when all input documents are concatenated into a single output doc)
				or an existing map, if appending to an existing imposition. 
Returns:	true = success | false = unrecoverable failure */

public boolean concatenate(HashMap<COSIndirectObject, COSCompositeObject> resMap)
{
	PDPage		currSrcPage = srcStatus.currPage != null ? srcStatus.currPage : srcStatus.nextPage();
	if (currSrcPage == null)
		return false;
	PDDocument	srcDoc 		= srcStatus.currDoc();
	if (srcDoc == null)
		return false;
	if (dstDoc == null)
		if (!createDestDocument(srcDoc))
			return false;
	if (resMap == null)
		resMap = new HashMap<COSIndirectObject, COSCompositeObject>();

	// for each signature
	while (currSrcPage != null)
	{
		PDPage			destPage	= (PDPage) PDPage.META.createNew();
		CSContent		destContent	= CSContent.createNew();
		CSCreator		destCreator	= CSCreator.createFromContent(destContent, currSrcPage);
		CDSRectangle	box = currSrcPage.getMediaBox().copy().normalize();
		destPage.setMediaBox(box);
		destCreator.copy(currSrcPage.getContentStream());
		destCreator.close();
		// add content to dest. page
		COSStream pageStream = destContent.createStream();
		pageStream.addFilter(COSName.constant("FlateDecode"));
		destPage.cosAddContents(pageStream);
		// add resources, if any
		if (currSrcPage.getResources() != null)
		{
			COSObject	cosResourcesCopy	= currSrcPage.getResources().cosGetObject().copyDeep(resMap);
			PDResources	pdResourcesCopy		= (PDResources) PDResources.META.createFromCos(cosResourcesCopy);
			destPage.setResources(pdResourcesCopy);
		}
		// add page to doc and release objects no longer needed
		dstDoc.addPageNode(destPage);
		destCreator	= null;		// a bit of paranoia!
		destContent	= null;
		destPage	= null;
		currSrcPage = srcStatus.nextPage();
	}
	return true;
}

/******************
	Save the destination document
*******************/

protected boolean save() /*throws IOException*/
{
	if (outputFileName == null)
	{
		System.err.println("No output file specified.");
		return false;
	}
	FileLocator locator = new FileLocator(outputFileName);
	try {
		dstDoc.save(locator, null);
		dstDoc.close();
	}
	catch (IOException e) {
		System.err.println("Error while saving to " + outputFileName + ": " + e.getMessage());
		return false;
	}
	return true;
}

/******************
	Getters
*******************/

public JPDImposition.Format	format()	{ return impo.format();					}

public String inputFileNames()			{ return srcStatus.inputFileNames();	}

public String outputFileName()			{ return outputFileName;				}

/******************
	Setters
*******************/

public void addSourceFileName(String srcFileName)
{
	srcStatus.addSrcDoc(srcFileName);
}

public void setFormat(String formatStr, String sheetsPerSignStr)
{
	// normalize and save params for later
	format	= JPDImposition.formatStringToVal(formatStr);
	if (sheetsPerSignStr != null)
		setMaxSheetsPerSign(sheetsPerSignStr);
}

public void setMaxSheetsPerSign(String sheetsPerSignStr)
{
	try {
		maxSheetsPerSign = Integer.parseInt(sheetsPerSignStr);
	}
	catch(NumberFormatException e) {
		maxSheetsPerSign = 1;
	}
	if (maxSheetsPerSign < 1)
		maxSheetsPerSign = 1;
}

public void setOutputFileName(String outputFileName)	{ this.outputFileName = outputFileName;	}

/******************
	Source documents
*******************

Methods to manage source documents. */

protected PDDocument openSrcDoc(String fileName)
{
	FileLocator	locator	= new FileLocator(fileName);
	PDDocument	doc;
	try {
		doc = PDDocument.createFromLocator(locator);
	}
	catch (IOException e) {
		System.err.println("Error opening file : " + fileName);
		return null;
	}
	catch (COSLoadException e) {
			System.err.println("Error parsing file : " + fileName);
			return null;
	}
	return doc;
}

protected boolean closeSrcDoc(PDDocument doc)
{
	if (doc == null)
		return true;
	try {
		doc.close();
		doc = null;
	}
	catch (IOException e) {
		System.err.println("Error closing document from file " + doc.getLocator().getFullName()
				+ ": " + e.getMessage());
		return false;
	}
	return true;
}

/******************
	Create single page
*******************

Creates a copy of single page, typically for a fold-out. */

protected boolean createSinglePage(PDPage currSrcPage, int gluePageNo, ArrayList<PDPage> singlePages,
		HashMap<COSIndirectObject, COSCompositeObject> resMap)
{
	PDPage			destPage	= (PDPage) PDPage.META.createNew();
	CSContent		destContent	= CSContent.createNew();
	CSCreator		destCreator	= CSCreator.createFromContent(destContent, currSrcPage);
	CDSRectangle	box = currSrcPage.getMediaBox().copy().normalize();
	destPage.setMediaBox(box);

	// add side page no., if supplied
	if (gluePageNo > 0)
	{
		if (impoFont == null)
		{
			impoFont		= PDFontType1.createNew(PDFontType1.FONT_Courier);
			impoFont.setEncoding(WinAnsiEncoding.UNIQUE);
			impoFontName	= COSName.create("jPDFMark");
		}
		COSObject	fontCopy	= impoFont.cosGetObject().copyDeep(resMap);
		PDFont		font		= (PDFont)PDFont.META.createFromCos(fontCopy);
		destCreator.textSetFont(impoFontName, font, (float)6.0);
		// place vertically at 1/6" from margin, mid-height
		// if gluing to odd page, place at left margin; if even, place at right margin
		destCreator.textSetTransform(0, 1, -1, 0,				// 90° counter-clockwise rotation
				(gluePageNo & 1) == 1 ? 12 : box.getWidth()-6,	// X translation
				box.getHeight()/2);								// Y translation
		// convert physical page no. to printed page no. and then from 0-based to 1-based
		destCreator.textShow("p. " + (gluePageNo - srcStatus.pageNoOffset(gluePageNo) + 1));
		destCreator.flush();
	}

	destCreator.copy(currSrcPage.getContentStream());
	destCreator.close();
	// add content to dest. page
	COSStream pageStream = destContent.createStream();
	pageStream.addFilter(COSName.constant("FlateDecode"));
	destPage.cosAddContents(pageStream);
	// add resources, if any
	if (currSrcPage.getResources() != null)
	{
		COSObject	cosResourcesCopy	= currSrcPage.getResources().cosGetObject().copyDeep(resMap);
		PDResources	pdResourcesCopy		= (PDResources) PDResources.META.createFromCos(cosResourcesCopy);
		destPage.setResources(pdResourcesCopy);
	}
	singlePages.add(destPage);

	return true;
}
/******************
	Merge a source resource dictionary into a destination resource dictionary
*******************/

protected void mergeResources(COSDictionary sourceDict, COSDictionary dest)
{
	@SuppressWarnings("unchecked")
	Iterator<COSDictionary.Entry>	iter	= sourceDict.entryIterator();
	// iterate on all the resource entries
	while(iter.hasNext())
	{
		// retrieve entry name
		COSDictionary.Entry	entry		= iter.next();
		COSName				entryName	= (COSName)entry.getKey();
		// if an entry with such a name already exists in the dest. dictionary
		if (dest.containsKey(entryName))
		{
			// if "ProcSet", we are dealing with COSarray's:
			// merge the source array into the dest. array
			if (entryName.stringValue().equals("ProcSet"))
			{
				COSArray	entryArray		= (COSArray)entry.getValue();
				COSArray	destEntryArr	= (COSArray)dest.get(entryName);
				// iterate on source array elements
				Iterator<COSDocumentElement>	iterName	= entryArray.basicIterator();
				while (iterName.hasNext())
				{
					COSName	srcName	= (COSName)iterName.next();
					boolean	found	= false;
					// compare this source COSName with each dest. COSName
					for (int i = 0; i < destEntryArr.size(); i++)
					{
						COSName destName = (COSName)destEntryArr.basicGet(i);
						// if same name, mark as found and stop
						if (srcName.stringValue().equals(destName.stringValue()) )
						{
							found = true;
							break;
						}
					}
					if (!found)				// if no such a name, add it to dest. array
						destEntryArr.basicAddSilent(srcName.copyShallow());
				}
			}
			// otherwise, we are dealing with COSDictionary'es:
			// add to the dest. dictionary all the entries in source dictionary not already present
			else
			{
				COSDictionary	entryDict		= (COSDictionary)entry.getValue();
				COSDictionary	destEntryDict	= (COSDictionary)dest.get(entryName);
				destEntryDict.addIfAbsent(entryDict);
			}
		}
		// if no entry with such a name in dest. dictionary, add it as it is
		else
		{
			COSObject	copy	= ((COSCompositeObject)entry.getValue()).copyShallow();
			dest.basicPutSilent(entryName, copy);
		}
	}
}

/******************
	Create destination document
*******************
Create a dest. document with the same type and version of the source document.

Only the "PDF" document type is supported; other types trigger application abort.
Only PDF versions up to "1.7" are supported; greater PDF versions are downgraded to 1.7 with a warning. */

protected boolean createDestDocument(PDDocument srcDoc)
{
	// retrieve and check source document type and version
	STDocType docType = srcDoc.cosGetDoc().stGetDoc().getDocType();
	if (!docType.getTypeName().equals("PDF"))
	{
		System.err.println("Unsupported document type: '"+ docType.getTypeName());
		return false;
	}
	if (docType.getVersion().compareTo("1.7") > 0)
	{
		System.err.println("Unsupported PDF version: "+ docType.getVersion() + ";\nthe source document may contain unsupported features.\nUsing PDF Version 1.7 instead.");
		docType.setVersion("1.7");
	}
	// create a dest. document of the same type and version of the source document
	COSDocument	cosDstDoc	= COSDocument.createNew(docType);
	COSObject	pageTree	= cosDstDoc.getCatalog().cosGetField(COSCatalog.DK_Pages);
	if (pageTree == null || pageTree == COSNull.NULL)
	{
		PDPageTree newPageTree = (PDPageTree) PDPageTree.META.createNew();
		cosDstDoc.getCatalog().cosSetField(COSCatalog.DK_Pages,
				newPageTree.cosGetObject());
	}
	dstDoc					= PDDocument.createFromCos(cosDstDoc);

	return true;
}

/******************
	Read parameter file
*******************

Reads and parses a parameter file, setting imposition parameters accordingly.

Parameters:	fileName: the file to parse
Returns:	true = success | false = unrecoverable failure */

public boolean readParamFile(String fileName)
{
	boolean			inFile			= false;
	boolean			inputFileSeen	= false;
	int				pageNoOffset	= 0;
	FileInputStream	paramStream;

	File			paramFile		= new File(fileName);
	String			filePath		= paramFile.getAbsoluteFile().getParent();

	try {
		paramStream = new FileInputStream(paramFile);
	} catch (FileNotFoundException e) {
		System.err.println("Error opening parameter file: " + fileName);
		return false;
	}
	try {
		XMLInputFactory	factory		= XMLInputFactory.newInstance();
		XMLStreamReader	reader		= factory.createXMLStreamReader(paramStream);

		while(reader.hasNext())
		{
			int	event	= reader.next();
			int	intVal;
			switch(event)
			{
/*			case XMLStreamConstants.START_DOCUMENT:
				break; */
			case XMLStreamConstants.START_ELEMENT:
				if (reader.getLocalName().toLowerCase().equals("jpdfimposition"))
				{
					inFile = true;
					break;
				}
				if (!inFile)
				{
					System.err.println("Parameter file " + fileName + " does not seem a jPDFImposition file");
					return false;
				}
				String	elementName	= reader.getLocalName();
				String	val			= reader.getAttributeValue(null, "value");
				if (val == null)
				{
					System.err.println("Missing \"value\" attribute in " + elementName + " tag. Aborting.");
					return false;
				}
				switch(elementName.toLowerCase())
				{
				case "jpdfimposition":
					break;
				case "input":
				{
					if (!inputFileSeen)			// if this is the first input file
					{
						srcStatus.init();		// reset source status to get rid of any CL source parameters
						inputFileSeen = true;
					}
					File file = new File(val);
					String fullFilePath = file.isAbsolute() ? val : filePath + File.separator + val;
					JPDISourceDoc doc = srcStatus.addSrcDoc(fullFilePath);
					if (doc == null)
						return false;
					// retrieve input attributes
					intVal	= getIntAttribute(reader, elementName, "pageNoOffset");
					if (intVal != INVALID_PARAM)
						pageNoOffset = intVal;
					doc.pageNoOffset = pageNoOffset;

					intVal	= getIntAttribute(reader, elementName, "fromPage");
					if (intVal != INVALID_PARAM)
						doc.setFromPage(intVal);

					intVal	= getIntAttribute(reader, elementName, "toPage");
					if (intVal != INVALID_PARAM)
						doc.setToPage(intVal);

					if (getBoolAttribute(reader, elementName, "signatureBreak") == true)
					{
						intVal = srcStatus.totPages();
						signBreakList.add(intVal);
					}
					break;
				}
				case "append":
				{
					if (!inputFileSeen)			// if no 'regular' input file
					{
						System.err.println("Cannot \"append\" without previous input documents; ignoring.");
						break;
					}
					File file = new File(val);
					String fullFilePath = file.isAbsolute() ? val : filePath + File.separator + val;
					JPDISourceDoc doc = srcStatus.addAppendDoc(fullFilePath);
					if (doc == null)
						return false;
					// retrieve input attributes
					intVal	= getIntAttribute(reader, elementName, "fromPage");
					if (intVal != INVALID_PARAM)
						doc.setFromPage(intVal);

					intVal	= getIntAttribute(reader, elementName, "toPage");
					if (intVal != INVALID_PARAM)
						doc.setToPage(intVal);
					break;
				}
				case "output":
				{
					File file = new File(val);
					outputFileName = file.isAbsolute() ? val : filePath + File.separator + val;
					break;
				}
				case "format":
				{
					format = JPDImposition.formatStringToVal(val);
					formatSubParam = 0;
					if (format == JPDImposition.Format.booklet
						&& getBoolAttribute(reader, elementName, "firstPageAsEven") == true)
								formatSubParam = -1;
					break;
				}
				case "sheetspersign":
					maxSheetsPerSign = getIntParam(val, elementName, 
							JPDImposition.DEFAULT_SHEETS_PER_SIGN);
					break;
				case "backoffsetx":
					pageOffsetX[BACK_PAGE] = getDoubleParam(val, elementName, 0.0) * JPDImposition.MM2PDF;
					break;
				case "backoffsety":
					pageOffsetY[BACK_PAGE] = getDoubleParam(val, elementName, 0.0) * JPDImposition.MM2PDF;
					break;
				case "frontoffsetx":
					pageOffsetX[FRONT_PAGE] = getDoubleParam(val, elementName, 0.0) * JPDImposition.MM2PDF;
					break;
				case "frontoffsety":
					pageOffsetY[FRONT_PAGE] = getDoubleParam(val, elementName, 0.0) * JPDImposition.MM2PDF;
					break;
				case "foldout":
					if (format != JPDImposition.Format.booklet)
					{
						System.err.println("\"foldout\" tag only valid with \"booklet\" format; ignoring.");
						break;
					}
					intVal = getIntParam(val, elementName, -1);
					// convert to physical page no., round down to nearest even number
					// and then convert from 1-based to 0-based
					intVal = ( (intVal + pageNoOffset) & 0xFFFE) - 1;
					foldOutList.add(intVal);
					break;
				case "pagesizehoriz":
					pageSizeX = getDoubleParam(val, elementName, 0.0) * JPDImposition.MM2PDF;
					break;
				case "pagesizevert":
					pageSizeY = getDoubleParam(val, elementName, 0.0) * JPDImposition.MM2PDF;
					break;
				default:
					System.err.println("Unknown parameter '" + elementName + "' in parameter file " + fileName);
				}
				break;
/*
			case XMLStreamConstants.CHARACTERS:
				tagContent = reader.getText().trim();
				break;
			case XMLStreamConstants.END_ELEMENT:
				break;
*/
			}
		}
	} catch (XMLStreamException e1) {
		System.err.println("Error parsing parameter file " + fileName + ": " + e1.getMessage());
		return false;
	}
	return true;
}

private int getIntParam(String tagContent, String elementName, int defaultVal)
{
	int	val;
	try {
		val = Integer.parseInt(tagContent);
	} catch (NumberFormatException e) {
		System.err.println("Invalid or empty " + elementName + " tag: " + tagContent
				+ "; setting to " + defaultVal);
		val = defaultVal;
	}
	return val;
}

private boolean getBoolAttribute(XMLStreamReader reader, String elementName, String attrName)
{
	boolean	boolVal	= false;
	String	strVal	= reader.getAttributeValue(null, attrName);
	if (strVal != null)
	{
		strVal = strVal.toLowerCase();
		if (strVal.equalsIgnoreCase("yes") || strVal.equals("true") || strVal.equals("1"))
			boolVal = true;
	}
	return boolVal;
}

private int getIntAttribute(XMLStreamReader reader, String elementName, String attrName)
{
	String	strVal	= reader.getAttributeValue(null, attrName);
	int		intVal	= INVALID_PARAM;
	if (strVal != null)
	{
		try {
			intVal = Integer.parseInt(strVal);
		} catch (NumberFormatException e) {
			System.err.println(elementName + " tag / " + attrName + " attribute: Invalid or empty value; ignoring.");
		}
	}
	return intVal;
}

private double getDoubleParam(String tagContent, String elementName, double defaultVal)
{
	double	val;
	try {
		val = Double.parseDouble(tagContent);
	} catch (NumberFormatException e) {
		System.err.println("Invalid or empty " + elementName + " tag: " + tagContent
				+ "; setting to " + defaultVal);
		val = defaultVal;
	}
	return val;
}

}
