/*
 * Copyright (c) 2011 Craig Setera.  All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.seterasoft.rockbox.tagcache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.seterasoft.rockbox.tagcache.util.MultiEndianDataOutputStream;

/**
 * Representation of one of the string-based tag files.  Depending
 * on the flags provided during construction, a tag file may or may 
 * not pad the values to an even 8 bytes.  In addition, the title
 * and filename files should not attempt to coalesce the same
 * strings into one.
 * 
 * @author Craig Setera
 *
 */
public class TagFile extends AbstractTagcacheFile {
	/** The number of bytes required by the header.  Used to calculate offsets. */
	private static final int HEADER_LENGTH = 12;
	
	private boolean padValues;
	private boolean coalesceValues;
	private int count;
	private Map<String, Integer> offsets;

	/**
	 * Construct a new tag file to hold string tag values.
	 * 
	 * @param littleEndian Whether the host is little endian.
	 * 
	 * @param padValues  Whether string values will be padded to a multiple of 8 bytes or
	 * not.
	 * 
	 * @param coalesceValues Whether identical string values will be coalesced to a single
	 * entry or not.
	 * 
	 */
	public TagFile(boolean littleEndian, boolean padValues, boolean coalesceValues) {
		super(littleEndian);

		this.padValues = padValues;
		this.coalesceValues = coalesceValues;
		
		offsets = new HashMap<String, Integer>();
	}

	/**
	 * Add the specified tag value and return the resulting offset.
	 * Depending on the setting for padding, this output may or may
	 * not be padded to a multiple of 8 bytes.
	 * 
	 * @param value The value to be added to the tag file.  This value
	 * may already exist and be coalesced to a different offset.
	 * 
	 * @param masterIndexEntryIndex The index of the entry in the index
	 * to be added to this entry as a reverse pointer.
	 * 
	 * @return
	 * @throws IOException
	 */
	public int addTagValue(String value, int masterIndexEntryIndex) 
		throws IOException 
	{
		Integer offset = coalesceValues ? offsets.get(value) : null;
		if (offset == null) {
			offset = addNewTagValue(value, masterIndexEntryIndex);
			count++;
			
			if (coalesceValues) {
				offsets.put(value, offset);
			}
		}
		
		return offset;
	}
	
	/**
	 * Write the data represented by this {@link TagFile} to the specified output folder.
	 * 
	 * @param outputFile
	 * @throws IOException
	 */
	public void writeFile(File outputFile)
		throws IOException
	{
		System.out.println("Writing tag file " + outputFile + " with " + count + " items");
		
		// Grab the data contents of the file
		dos.flush();
		byte[] bytes = bos.toByteArray();
		
		// Open up a new output file
		FileOutputStream fos = new FileOutputStream(outputFile);
		dos = new MultiEndianDataOutputStream(fos);

		/* Write the header
		
		Bytes	Content
		4	 Database version
		4	 Size (in bytes) of the non-header part of the file
		4	 Number of entries in the file 
		*/
		writeInt(VERSION);
		writeInt(bytes.length);
		writeInt(count);

		// Write the actual data
		dos.write(bytes);
		dos.close();
	}
	
	/**
	 * Add a new tag to tag file.  This will happen if it doesn't already exist
	 * in the file or if tag values are not being coalesced.
	 * 
	 * 
	 * @param value The value to be added to the tag file.
	 * 
	 * @param masterIndexEntryIndex The index of the entry in the index
	 * to be added to this entry as a reverse pointer.
	 * 
	 * @return the offset of this entry into the file.
	 * 
	 * @throws IOException
	 */
	private Integer addNewTagValue(String value, int masterIndexEntryIndex) 
		throws IOException 
	{
		if (dos == null) {
			initializeStreams();
		}

		// Make sure we don't end up with null values
		if (value == null) value = "";
		
		// Capture the current offset in the file, accounting for the fact
		// that we don't write the offset to our byte array output stream
		int offset = dos.size() + HEADER_LENGTH;
		
		// Convert the string and figure out our length
		byte[] bytes = value.getBytes("UTF-8");
		int stringLength = bytes.length + 1;  // For required null padding
		int totalStringLength = stringLength;
		
		// Contrary to the wiki documentation at this point, the filename database
		// file does not pad entries to multiples of 8.  Only pad if necessary.
		if (padValues) {
			// The tag entry's data is always padded with Xes (after the null byte) so that the data length is 4+8*n (where n is an integer)
			int multiplier = stringLength / 8;
			if ((stringLength % 8) != 0) multiplier++;
	
			totalStringLength = multiplier * 8;
		}
		
		// Write the entry header
		writeInt(totalStringLength);
		writeInt(masterIndexEntryIndex);
		
		// Write the entry data
		dos.write(bytes);
		dos.write(0);
		
		// Pad as necessary with 'X' characters
		if (padValues) {
			int padding = totalStringLength - stringLength;
			for (int i = 0; i < padding; i++) {
				dos.write(88); // 'X'
			}
		}
		
		return offset;
	}
}
