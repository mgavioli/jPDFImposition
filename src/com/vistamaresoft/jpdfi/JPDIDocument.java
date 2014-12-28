/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	JPDIDocument.java - Sub-class of PDDocument, for creating an imposed document from an original PDDocument

	Created by : Maurizio M. Gavioli 2014-12-17
*****************************/

package com.vistamaresoft.jpdfi;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

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
//import de.intarsys.pdf.cos.COSDocument;
import de.intarsys.pdf.cos.COSIndirectObject;
import de.intarsys.pdf.cos.COSName;
import de.intarsys.pdf.cos.COSNull;
import de.intarsys.pdf.cos.COSObject;
import de.intarsys.pdf.cos.COSStream;
import de.intarsys.pdf.parser.COSLoadException;
import de.intarsys.pdf.pd.PDDocument;
//import de.intarsys.pdf.pd.PDForm;
import de.intarsys.pdf.pd.PDPage;
//import de.intarsys.pdf.pd.PDPageNode;
import de.intarsys.pdf.pd.PDPageTree;
import de.intarsys.pdf.pd.PDResources;
import de.intarsys.pdf.st.STDocType;
import de.intarsys.tools.locator.FileLocator;

/******************
	CLASS JPDIDocument
*******************/

public class JPDIDocument extends Object
{
private PDDocument				dstDoc, srcDoc;
private JPDImposition.Format	format;
private JPDImposition			impo;
private String					inputFileName;
private int						maxSheetsPerSign;
private String					outputFileName;
private double					pageOffsetX[]	= { 0.0, 0.0 };
private double					pageOffsetY[]	= { 0.0, 0.0 };

private static final int		FRONT_PAGE		= 0;
private static final int		BACK_PAGE		= 1;
private static final double		MM2PDF			= 72 / 25.4;

public static final boolean		useMerger		= true;

/******************
	C'tors
*******************/

public JPDIDocument()
{
	srcDoc				= null;
	init();
}

public JPDIDocument(String filename)
{
	if (filename != null)
		setSourceFileName(filename);
	init();
}

private void init()
{
	dstDoc				= null;
	impo				= new JPDImposition();
	format				= impo.format();
	maxSheetsPerSign	= impo.maxSheetsPerSignature();
	pageOffsetX[FRONT_PAGE]	= pageOffsetX[BACK_PAGE]
			= pageOffsetY[FRONT_PAGE] = pageOffsetY[BACK_PAGE] = 0.0;
	inputFileName		= outputFileName = null;
}

/******************
	Apply the imposition
*******************
Fills the document with data from sourceDoc according to impo. */

public boolean impose()
{
	if (srcDoc == null)
		return false;
	if (!createDestDocument())
		return false;
	PDPageTree	pageTree 	= srcDoc.getPageTree();
	impo.setFormat(format, maxSheetsPerSign, pageTree.getCount());
	HashMap<COSIndirectObject, COSCompositeObject> resMap =
			new HashMap<COSIndirectObject, COSCompositeObject>();
	JPDIResourceMerger merger = null;
	PDPage	currSrcPage	= pageTree.getFirstPage();
	int		currSignNo	= 0;
	// for each signature
	while (currSrcPage != null)
	{
		int		numOfDestPages = impo.numOfDestPagesPerSignature(currSignNo);
		if (useMerger)
			if (merger == null)
				merger = new JPDIResourceMerger(dstDoc, numOfDestPages);
			else
				merger.setNumOfDestPages(numOfDestPages);
		// get destination crop box from source page crop box
		CDSRectangle box	= currSrcPage.getMediaBox().copy().normalize();
		float srcPageHeight	= box.getHeight();
		float srcPageWidth	= box.getWidth();
		box.setHeight(srcPageHeight * impo.numOfRows());
		box.setWidth (srcPageWidth  * impo.numOfCols());

		// create arrays to hold destination pages (and their appendages) for the whole signature
		PDPage			destPage[]		= new PDPage[numOfDestPages];
		CSContent		destContent[]	= new CSContent[numOfDestPages];
		CSCreator		destCreator[]	= new CSCreator[numOfDestPages];
		COSDictionary	destResDict[]	= new COSDictionary[numOfDestPages];

		// instantiate new pages for the whole signature
		for (int pageNo = 0; pageNo < numOfDestPages; pageNo++)
		{
			destPage[pageNo]	= (PDPage) PDPage.META.createNew();
			destPage[pageNo].setMediaBox(box.copy());
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

			// set PAGE TRANSFORMATION into destination place

			double	rot		= impo.pageDestRotation(currSignPageNo, currSignNo) * Math.PI / 180.0;
			double	cosRot	= Math.cos(rot);
			double	sinRot	= Math.sin(rot);
			double	offsetX	= impo.pageDestOffsetX(currSignPageNo, currSignNo, srcPageWidth)
					+ pageOffsetX[destPageNo & 1];
			double	offsetY	= impo.pageDestOffsetY(currSignPageNo, currSignNo, srcPageHeight)
					+ pageOffsetY[destPageNo & 1];
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
			currSrcPage = currSrcPage.getNextPage();
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

public JPDImposition.Format	format()	{ return impo.format();		}

public String getInputFileName()		{ return inputFileName;		}

public String getOutputFileName()		{ return outputFileName;	}

/******************
	Setters
*******************/

public void setFormat(String formatStr, String sheetsPerSignStr)
{
	// normalize and save params for later
	format	= JPDImposition.formatStringToVal(formatStr);
	String maxSheetsPerSignStr = (sheetsPerSignStr != null) ? sheetsPerSignStr : formatStr;
	try {
		maxSheetsPerSign = Integer.parseInt(maxSheetsPerSignStr);
	}
	catch(NumberFormatException e) {
		maxSheetsPerSign = 1;
	}
	if (maxSheetsPerSign < 1)
		maxSheetsPerSign = 1;
}

public void setOutputFileName(String outputFileName)	{ this.outputFileName = outputFileName;	}

public void	setSourceDoc(PDDocument doc)				{ srcDoc = doc;	}

public void setSourceFileName(String filename) /*throws IOException, COSLoadException*/
{
	inputFileName	= filename;

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

protected boolean createDestDocument()
{
//	dstDoc = PDDocument.createNew();

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
	FileInputStream	paramStream;
	File			paramFile	= new File(fileName);
	String			filePath	= paramFile.getAbsoluteFile().getParent();
	boolean			inFile		= false;
	String			tagContent	= null;
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
			int event = reader.next();
			switch(event)
			{
/*			case XMLStreamConstants.START_DOCUMENT:
				break; */
			case XMLStreamConstants.START_ELEMENT:
				if (reader.getLocalName().toLowerCase().equals("jpdfimposition"))
					inFile = true;
				break;

			case XMLStreamConstants.CHARACTERS:
				tagContent = reader.getText().trim();
				break;

			case XMLStreamConstants.END_ELEMENT:
				if (!inFile)
				{
					System.err.println("Parameter file " + fileName + " does not seem a jPDFImposition file");
					return false;
				}
				switch(reader.getLocalName().toLowerCase())
				{
				case "jpdfimposition":
					break;
				case "input":
				{
					File file = new File(tagContent);
					String fullFilePath = file.isAbsolute() ?
							tagContent : filePath + File.separator + tagContent;
					setSourceFileName(fullFilePath);
					break;
				}
				case "output":
				{
					File file = new File(tagContent);
					outputFileName = file.isAbsolute() ?
							tagContent : filePath + File.separator + tagContent;
					break;
				}
				case "format":
					format = JPDImposition.formatStringToVal(tagContent);
					break;
				case "sheetspersign":
					try {
						maxSheetsPerSign = Integer.parseInt(tagContent);
					} catch (NumberFormatException e) {
						System.err.println("Invalid or empty \"sheetsPerSign\" tag: " + tagContent
								+ "; setting to " + JPDImposition.DEFAULT_SHEETS_PER_SIGN);
						maxSheetsPerSign = JPDImposition.DEFAULT_SHEETS_PER_SIGN;
					}
					break;
				case "backoffsetx":
					try {
						pageOffsetX[BACK_PAGE] = Double.parseDouble(tagContent) * MM2PDF;
					} catch (NullPointerException | NumberFormatException e) {
						System.err.println("Invalid or empty \"backOffsetX\" tag: " + tagContent
								+ "; setting to 0");
						pageOffsetX[BACK_PAGE] = 0.0;
					}
					break;
				case "backoffsety":
					try {
						pageOffsetY[BACK_PAGE] = Double.parseDouble(tagContent) * MM2PDF;
					} catch (NullPointerException | NumberFormatException e) {
						System.err.println("Invalid or empty \"backOffsetY\" tag: " + tagContent
								+ "; setting to 0");
						pageOffsetY[BACK_PAGE] = 0.0;
					}
					break;
				case "frontoffsetx":
					try {
						pageOffsetX[FRONT_PAGE] = Double.parseDouble(tagContent) * MM2PDF;
					} catch (NullPointerException | NumberFormatException e) {
						System.err.println("Invalid or empty \"frontOffsetX\" tag: " + tagContent
								+ "; setting to 0");
						pageOffsetX[FRONT_PAGE] = 0.0;
					}
					break;
				case "frontoffsety":
					try {
						pageOffsetY[FRONT_PAGE] = Double.parseDouble(tagContent) * MM2PDF;
					} catch (NullPointerException | NumberFormatException e) {
						System.err.println("Invalid or empty \"frontOffsetY\" tag: " + tagContent
								+ "; setting to 0");
						pageOffsetY[FRONT_PAGE] = 0.0;
					}
					break;
				default:
					System.err.println("Unknown parameter '" + tagContent + "' in parameter file " + fileName);
				}
				break;
			}
		}
	} catch (XMLStreamException e1) {
		System.err.println("Error parsing parameter file " + fileName + ": " + e1.getMessage());
		return false;
	}
	return true;
}

}
