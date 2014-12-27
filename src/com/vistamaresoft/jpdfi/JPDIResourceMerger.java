/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	ResourceMerger.java - Manages merging of resources from multiple pages

	Created by : Maurizio M. Gavioli 2014-12-25
*****************************/

package com.vistamaresoft.jpdfi;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import de.intarsys.pdf.content.CSContent;
import de.intarsys.pdf.content.CSOperation;
import de.intarsys.pdf.cos.COSArray;
import de.intarsys.pdf.cos.COSCompositeObject;
import de.intarsys.pdf.cos.COSDictionary;
//import de.intarsys.pdf.cos.COSDocumentElement;
import de.intarsys.pdf.cos.COSName;
import de.intarsys.pdf.cos.COSObject;
import de.intarsys.pdf.pd.PDDocument;
import de.intarsys.pdf.pd.PDPage;
import de.intarsys.pdf.pd.PDResources;

/******************
	CLASS JPDImposition
*******************/

public class JPDIResourceMerger
{
protected PDDocument					destDoc;
protected HashMap<COSObject, COSName>	docMap;
protected int							uniqueId;
protected HashMap<COSObject, COSName>[]	pageMap;
protected COSDictionary[]				pageRes;

/******************
	C'tor
*******************/

public JPDIResourceMerger(PDDocument destDoc, int numOfDestPages)
{
	this.destDoc	= destDoc;
	docMap			= new HashMap<COSObject, COSName>();
	uniqueId		= 1;
	setNumOfDestPages(numOfDestPages);
}

/******************
	Release destination pages
******************

Releases any existing data for destination pages.

Parameters:	none
Returns:	none */

public void releaseDestPages()
{
	if (pageMap != null)
	{
		for (int i = 0; i < pageMap.length; i++)
			pageMap[i] = null;
		pageMap = null;
	}
	if (pageRes != null)
	{
		for (int i = 0; i < pageRes.length; i++)
			pageRes[i] = null;
		pageRes = null;
	}
}

/******************
	Set the number of destination pages
******************

Sets the number of destination pages, releasing any existing page data and creating new structures for
new pages.

Parameters:	numOfDestPage:	the new number of destination pages
Returns:	true = success | false failure */

@SuppressWarnings("unchecked")
public boolean setNumOfDestPages(int numOfDestPages)
{
	releaseDestPages();
	pageMap			= (HashMap<COSObject, COSName>[])new HashMap[numOfDestPages];
	pageRes			= new COSDictionary[numOfDestPages];
	for (int i = 0; i < numOfDestPages; i++)
	{
		pageMap[i]	= new HashMap<COSObject, COSName>();
		pageRes[i]	= PDResources.META.createNew().cosGetDict();
	}
	return true;
}

/******************
	Merge a source page into a destination page
******************

Parameters:	destPageIdx:	the index of the destination page to merge into
			srcPage:		the source page to merge from
Returns:	true = success | false failure */

public boolean merge(int destPageIdx, PDPage srcPage)
{
	HashMap<COSName, COSName>	renameList	= new HashMap<COSName, COSName>();

	if (destPageIdx < 0 || destPageIdx > pageMap.length)
		return false;

	COSDictionary					srcResDict	= srcPage.getResources().cosGetDict();
	@SuppressWarnings("unchecked")
	Iterator<COSDictionary.Entry>	iter		= srcResDict.entryIterator();
	// iterate on all the source resource entries (sub-tables)
	while(iter.hasNext())
	{
		// retrieve sub-table name
		COSDictionary.Entry	srcTable		= iter.next();
		COSName				srcTableName	= (COSName)srcTable.getKey();
		// "/ProcSet" table is different
		boolean				isProcSet		= srcTableName.stringValue().equals("ProcSet");

		// if this sub-table not yet in destination page, add it as it is
		if (!pageRes[destPageIdx].containsKey(srcTableName))
		{
			COSObject	copy	= ((COSCompositeObject)srcTable.getValue()).copyShallow();
			pageRes[destPageIdx].basicPutSilent(srcTableName, copy);
			// if not "/ProcSet", also add each sub-table item to page map
			if (!isProcSet)
			{
				COSDictionary	srcTableDict	= (COSDictionary)srcTable.getValue();
				@SuppressWarnings("unchecked")
				Iterator<COSDictionary.Entry>	srcTableIter	= srcTableDict.entryIterator();
				// also add each sub-table item to the page map
				while (srcTableIter.hasNext())
				{
					COSDictionary.Entry	res	= srcTableIter.next();
					pageMap[destPageIdx].put((COSObject)res.getValue(), (COSName)res.getKey());
				}
			}
		}

		// if this sub-table already present in destination page,
		// iterate on each sub-table item
		else
		{
			if (!isProcSet)			// SUB-TABLE IS A DICTIONARY
			{
				COSDictionary	srcTableDict	= (COSDictionary)srcTable.getValue();
				COSDictionary	dstTableDict	= (COSDictionary)pageRes[destPageIdx].get(srcTableName);
				@SuppressWarnings("unchecked")
				Iterator<COSDictionary.Entry>	srcTableIter	= srcTableDict.entryIterator();
				// for each source table item
				while (srcTableIter.hasNext())
				{
					COSDictionary.Entry	res			= srcTableIter.next();
					COSName				resName		= (COSName)res.getKey();
					COSObject			resValue	= (COSObject)res.getValue();
					// look in PAGE MAP for this object, retrieving its name if object found
					COSName dstPageName	= pageMap[destPageIdx].get(resValue);
					// check if PAGE MAP already uses this name for an object
					boolean				nameIsUsed	= false;
					for(Map.Entry<COSObject, COSName> entry : pageMap[destPageIdx].entrySet())
						if (entry.getValue().stringValue().equals(resName.stringValue()) )
						{
							nameIsUsed = true;
							break;
						}
					// if page doesn't contain this object, add it to map and to dictionary
					if (dstPageName == null)
					{
						if (nameIsUsed)
						{
							dstPageName = COSName.create(uniqueNameString());
							renameList.put(resName, dstPageName);
						}
						else
							dstPageName = resName;
						pageMap[destPageIdx].put(resValue, dstPageName);
						dstTableDict.basicPutSilent(dstPageName, resValue);
					}
					// if page already contains this object, check its name
					else
						// if the dest. page knows the object under a different name
						// the name in the source page shall be changed into the dest. page name
						if (!dstPageName.stringValue().equals(resName.stringValue()))
							renameList.put(resName, dstPageName);
						// if source pages uses the same name as the dest. page, do nothing
				}
			}

			else					// "/ProcSet": SUB-TABLE IS AN ARRAY OF COSName's
			{
				COSArray	srcTableArray	= (COSArray)srcTable.getValue();
				COSArray	dstTableArray	= (COSArray)pageRes[destPageIdx].get(srcTableName);
				// iterate on source array elements
				for (COSObject srcName : srcTableArray)
				{
					boolean	found	= false;
					String	srcStr	= ((COSName)srcName).stringValue();
					// compare this source COSName with each dest. COSName
					for (COSObject destName : dstTableArray)
					{
						// if same name, mark as found and stop
						if (srcStr.equals( ((COSName)destName).stringValue()) )
						{
							found = true;
							break;
						}
					}
					if (!found)				// if no such a name, add it to dest. array
						dstTableArray.basicAddSilent(srcName.copyShallow());
				}
			}
		}
	}
	// update in the source page contents all the COSName which need to be changed
	if (renameList.size() > 0)
		renameInPage(srcPage, renameList);

	return true;
}

/******************
	Get page resources
******************

Parameters:	destPageIdx:	the index of the destination page to get resources for
Returns:	a COSDictionary with the page accumulated resources */

public final COSDictionary getResources(int destPageIdx)
{
	return pageRes[destPageIdx];
}
/******************
	RENAME PAGE CONTENTS
******************

Scans the given page contents and renames all the operand COSName's in renameList.

Parameters:	page:		the page to work on
			renameList:	a list of COSName to rename
Returns:	none */

protected void renameInPage(PDPage page, HashMap<COSName, COSName>renameList)
{
	CSContent	content		= page.getContentStream();
	int			numOfOps	= content.size();
	// iterate on operations
	for (int i = 0; i < numOfOps; i++)
	{
		CSOperation	op				= content.getOperation(i);
		int			numOfOperands	= op.operandSize();
		// for each operation, iterate on operands
		for (int j = 0; j < numOfOperands; j++)
		{
			COSObject	operand = op.getOperand(j);
			if (operand instanceof COSName)					// we all deal with COSName operands
			{
				COSName	newName = renameList.get(operand);	// is this COSName in the rename list?
				// if in the rename list, set this operand to the new COSName
				if (newName != null)
					op.setOperand(j, newName);
			}
		}
	}
}

/******************
	Create a unique String
******************

Creates a (hopefully) unique string for use as a COSName in the current destination document.

Parameters:	none
Returns:	a (hopefully) unique String */

protected String uniqueNameString()
{
	return new String("JPDI" + uniqueId++);
}

}
