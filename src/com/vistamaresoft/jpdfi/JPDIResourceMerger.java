/****************************
	j P D F i  -  A Java application to apply an imposition to a PDF document.

	ResourceMerger.java - Manages merging of resources from multiple pages

	Created by : Maurizio M. Gavioli 2014-12-25

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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
//import java.util.Observable;
//import java.util.Observer;

import de.intarsys.pdf.content.CSContent;
import de.intarsys.pdf.content.CSOperation;
import de.intarsys.pdf.cos.COSArray;
import de.intarsys.pdf.cos.COSCompositeObject;
import de.intarsys.pdf.cos.COSDictionary;
import de.intarsys.pdf.cos.COSName;
import de.intarsys.pdf.cos.COSObject;
import de.intarsys.pdf.pd.PDPage;
import de.intarsys.pdf.pd.PDResources;

/******************
	CLASS JPDImposition
*******************/

public class JPDIResourceMerger /*implements Observer*/
{
// FIELDS
protected int							uniqueId;
protected HashMap<COSName, COSObject>[]	pageMap;	// map of resources used in each destination page
protected COSDictionary[]				pageRes;	// dictionary of resources for each destination page

/******************
	C'tor
*******************/

public JPDIResourceMerger(int numOfDestPages)
{
	uniqueId		= 1;
	setNumOfDestPages(numOfDestPages);
}

/******************
	Set the number of destination pages
******************

Sets the number of destination pages, releasing any existing page data and creating new structures for
new pages.

Parameters:	numOfDestPage:	the new number of destination pages
Returns:	true = success | false = failure */

@SuppressWarnings("unchecked")
public boolean setNumOfDestPages(int numOfDestPages)
{
	releaseDestPages();
	pageMap			= (HashMap<COSName, COSObject>[])new HashMap[numOfDestPages];
	pageRes			= new COSDictionary[numOfDestPages];
	for (int i = 0; i < numOfDestPages; i++)
	{
		pageMap[i]	= new HashMap<COSName, COSObject>();
		pageRes[i]	= PDResources.META.createNew().cosGetDict();
	}
	return true;
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
	Merge resources of a source page into a destination page
******************

Parameters:	destPageIdx:	the index of the destination page to merge into
			srcPage:		the source page to merge from
Returns:	true = success | false failure */

public boolean merge(int destPageIdx, PDPage srcPage)
{
	HashMap<COSName, COSName>	renameList	= merge(destPageIdx, srcPage.getResources());
	if (renameList == null)
		return false;
	// update in the source page contents all the COSName which need to be changed
	if (renameList.size() > 0)
		renameInPage(srcPage, renameList);
	return true;
}

public HashMap<COSName, COSName> merge(int destPageIdx, PDResources srcRes)
{
	if (destPageIdx < 0 || destPageIdx > pageMap.length)
		return null;

	HashMap<COSName, COSName>		renameList	= new HashMap<COSName, COSName>();
	COSDictionary					srcResDict	= srcRes.cosGetDict();
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
					pageMap[destPageIdx].put((COSName)res.getKey(), (COSObject)res.getValue());
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
					COSName				dstPageName;
					// look in PAGE MAP for this object, retrieving its name if object found
//					COSName dstPageName	= pageMap[destPageIdx].get(resValue);
					boolean				objIsUsed	= pageMap[destPageIdx].containsValue(resValue);
					// check if PAGE MAP already uses this name for an object
					boolean				nameIsUsed	= false;
					for(COSName name : pageMap[destPageIdx].keySet())
						if (name.stringValue().equals(resName.stringValue()) )
						{
							nameIsUsed = true;
							break;
						}
					// if page doesn't contain this object, add it to map and to dictionary
					if (!objIsUsed)
					{
						if (nameIsUsed)
						{
							dstPageName = COSName.create(uniqueNameString());
							renameList.put(resName, dstPageName);
						}
						else
							dstPageName = resName;
						dstTableDict.basicPutSilent(dstPageName, resValue);
						pageMap[destPageIdx].put(dstPageName, resValue);
					}
					// if page already contains this object, check its name
					else
					{
						dstPageName = COSName.create("");
						for(Map.Entry<COSName, COSObject> entry : pageMap[destPageIdx].entrySet())
							if (entry.getValue() == resValue)
							{
								dstPageName = entry.getKey();
								break;
							}
						// if the dest. page knows the object under a different name
						// the name in the source page shall be changed into the dest. page name
						if (!dstPageName.stringValue().equals(resName.stringValue()))
							renameList.put(resName, dstPageName);
						// if source pages uses the same name as the dest. page, do nothing
					}
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

	return renameList;
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
	Rename page contents
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

/******************
	UPDATE
******************

Called when the source document changes. Keeps all names in page maps to avoid duplicates,
but associates them with null objects, as COSOjbect's of the new document are different
from any COSObject of the previous document, regardless of names.

Parameters:	who:	the Observable sending the event
			what:	the actual event
Returns:	none */
/*
public void update(Observable who, Object what)
{
	if (!what.equals(JPDIMsgs.MSG_NEW_DOC))			// we are only interested in the MSG_NEW_DOC
		return;
	for (int i = 0; i < pageMap.length; i++)
//		pageMap[i].clear();
		for (COSName obj : pageMap[i].keySet())
			pageMap[i].put(obj, null);
}
*/
}
