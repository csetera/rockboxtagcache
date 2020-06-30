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
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.reference.GenreTypes;
import org.seterasoft.rockbox.tagcache.util.MultiEndianDataOutputStream;

/**
 * Implementation of handling for the database_idx.tcd file.
 * 
 * @author Craig Setera
 */
public class IndexFile extends AbstractTagcacheFile {
	/** The number of bytes required by the header.  Used to calculate offsets. */
	public static final int HEADER_LENGTH = 24;

	/**
	 * The Tag field values for each individual tag file (database_n.tcd)
	 */
	public static final FieldKey[] TAG_KEYS = new FieldKey[] {
		FieldKey.ARTIST, 			// database_0.tcd 
		FieldKey.ALBUM,  			// database_1.tcd
		FieldKey.GENRE,				// database_2.tcd
		FieldKey.TITLE,				// database_3.tcd
		null,						// database_4.tcd
		FieldKey.COMPOSER,			// database_5.tcd
		FieldKey.COMMENT,			// database_6.tcd
		FieldKey.ALBUM_ARTIST,		// database_7.tcd
		FieldKey.GROUPING			// database_8.tcd
	};
	
	/**
	 * Handle mappings of genre from old style numeric to new-style
	 * text values.  JAudioTagger will return genre strings of the
	 * format "(xx)" where "xx" is a number to be matched against
	 * the old-style genre values.
	 */
	private static final Pattern GENRE_MATCH_PATTERN = Pattern.compile("\\((\\d\\d)\\)");

	private int count;
	private TagFile[] tagFiles;
	
	/**
	 * Construct a new database index file with the specified endianness.
	 * 
	 * @param littleEndian
	 */
	@SuppressWarnings("static-access")
	public IndexFile(boolean littleEndian) {
		super(littleEndian);
		
		this.tagFiles = new TagFile[TAG_KEYS.length];
		for (int i = 0; i < this.TAG_KEYS.length; i++) {
			boolean padValues = (i == 4) ? false : true;
			boolean coalesceValues = ((i == 3) || (i == 4)) ? false : true;
			
			this.tagFiles[i] = new TagFile(littleEndian, padValues, coalesceValues);
		}
	}
	
	/**
	 * Add a new {@link AudioFile} to the tagcache.
	 * 
	 * @param diskname The "name" to be used for generation of the filename
	 * in the database.
	 * 
	 * @param rootFolder The local file system root folder for relative path calculation.
	 * 
	 * @param file The file to be added to the database.
	 * 
	 * @throws IOException
	 */
	public void addFile(String diskname, File rootFolder, AudioFile file) 
		throws IOException 
	{
/*
Bytes	Content	Notes
4	 artist	 byte offset for tag file
4	 album	 byte offset for tag file
4	 genre	 byte offset for tag file
4	 title	 byte offset for tag file
4	 filename	 byte offset for tag file
4	 composer	 byte offset for tag file
4	 comment	 byte offset for tag file
4	 albumartist	 byte offset for tag file
4	 grouping	 byte offset for tag file
4	 year	  
4	 discnumber	  
4	 tracknumber	  
4	 bitrate	  
4	 length	 In milliseconds
4	 playcount	  
4	 rating	  
4	 playtime	  
4	 lastplayed	  
4	 commitid	  
4	 mtime	 see below
4	 flags	 see below
4	 lastoffset
 */
		
		if (dos == null) {
			initializeStreams();
		}

		// Add the string-based tags for this file to the various
		// TagFile instances.
		addStringTags(diskname, rootFolder, file);
		
		// Add the non-string values to the index entry
		AudioHeader audioHeader = file.getAudioHeader();
		Tag tag = file.getTag();
		writeInt(getStringTagFieldAsInteger(tag, FieldKey.YEAR, 1990));
		writeInt(getStringTagFieldAsInteger(tag, FieldKey.DISC_NO, 1));
		writeInt(getStringTagFieldAsInteger(tag, FieldKey.TRACK, 1));
		writeInt((int) audioHeader.getBitRateAsNumber());
		writeInt(audioHeader.getTrackLength() * 1000); // Length in milliseconds
		writeInt(0); // Playcount
		writeInt(0); // Rating
		writeInt(0); // Play time		
		writeInt(0); // Lastplayed
		writeInt(0); // Commitid
		writeInt(getMtime(file));
		writeInt(0); // flags
		writeInt(0); // lastoffset
		
		// Keep track of how many entries we have.
		count++;
	}

	/**
	 * Write the database files that have accumulated to the specified
	 * output folder.
	 * 
	 * @param outputFolder
	 * @throws IOException
	 */
	public void writeFiles(File outputFolder) 
		throws IOException 
	{
		if (dos != null) {
			writeIndexFile(outputFolder);
			writeTagFiles(outputFolder);
		}
	}
	
	/**
	 * Return the appropriate Genre String value, accounting for
	 * genres of the format "(xx)".
	 * 
	 * @param tag
	 * @param fieldKey
	 * @return
	 */
	private String getGenre(Tag tag, FieldKey fieldKey) {
		String genre = tag.getFirst(fieldKey);

		Matcher matcher = GENRE_MATCH_PATTERN.matcher(genre);
		if (matcher.matches()) {
			int id = Integer.parseInt(matcher.group(1));
			genre = GenreTypes.getInstanceOf().getValueForId(id);
		}
		
		return genre;
	}

	/**
	 * Return a device-based filename for the specified audio file, accounting for
	 * which storage media it is located on and its relative path.
	 * 
	 * @param diskname The "name" to be used for generation of the filename
	 * in the database.
	 * 
	 * @param rootFolder The local file system root folder for relative path calculation.
	 * 
	 * @param file The file path to be calculated
	 * 
	 * @return
	 */
	private String getMappedFilename(String diskname, File rootFolder, AudioFile file) {
		// Find the file system path relative to the root path on
		// the PC host
		String filePath = file.getFile().getAbsolutePath();
		String rootFolderPath = rootFolder.getAbsolutePath();
		String pathSubstring = filePath.substring(rootFolderPath.length());
		
		// Generate the appropriate qualified path on the device
		StringBuilder sb = new StringBuilder(diskname);
		if (!pathSubstring.startsWith("/")) sb.append("/");
		sb.append(pathSubstring);
		
		return sb.toString();
	}

	/**
	 * Calculate an FAT file system "mtime" value for the last modified value
	 * of the specified AudioFile.  The format of that value is calculated as:
	 * <pre>
Date Format. A FAT directory entry date stamp is a 16-bit field that is basically a date relative to the 
MS-DOS epoch of 01/01/1980. Here is the format (bit 0 is the LSB of the 16-bit word, bit 15 is the 
MSB of the 16-bit word): 
Bits 0–4: Day of month, valid value range 1-31 inclusive. 
Bits 5–8: Month of year, 1 = January, valid value range 1–12 inclusive. 
Bits 9–15: Count of years from 1980, valid value range 0–127 inclusive (1980–2107).
 
Time Format. A FAT directory entry time stamp is a 16-bit field that has a granularity of 2 seconds. 
Here is the format (bit 0 is the LSB of the 16-bit word, bit 15 is the MSB of the 16-bit word). 
Bits 0–4: 2-second count, valid value range 0–29 inclusive (0 – 58 seconds). 
Bits 5–10: Minutes, valid value range 0–59 inclusive. 
Bits 11–15: Hours, valid value range 0–23 inclusive. 
The valid time range is from Midnight 00:00:00 to 23:59:58	 
	 * </pre>
	 */
	private int getMtime(AudioFile file) {
		Calendar modifiedCalendar = Calendar.getInstance();
		modifiedCalendar.setTimeInMillis(file.getFile().lastModified());

		int year = modifiedCalendar.get(Calendar.YEAR) - 1980; // 7 bits - (32 - 7 = 25)
		int monthOfYear = modifiedCalendar.get(Calendar.MONTH); // 4 bits - (25 - 4 = 21)
		int dayOfMonth = modifiedCalendar.get(Calendar.DAY_OF_MONTH); // 5 bits - (21 - 5 = 16)

		int hours = modifiedCalendar.get(Calendar.HOUR_OF_DAY); // 5 bits - (16 - 5 = 11)
		int minutes = modifiedCalendar.get(Calendar.MINUTE); // 6 bits - (11 - 6 = 5)
		int twoSecondCount = modifiedCalendar.get(Calendar.SECOND) / 2; // 5 bits

		return 
			(year << 25) |
			(monthOfYear << 21) |
			(dayOfMonth << 16) |
			(hours << 11) |
			(minutes << 5) |
			twoSecondCount;
	}

	/**
	 * Return the specified tag value as an integer.  If not found or if it
	 * can't be parsed into an integer, use the specified default value.
	 * 
	 * @param tag The AudioFile tag
	 * @param key The tag field to be returned.
	 * @param defaultValue default value if the tag value can't be found or
	 * converted
	 * 
	 * @return
	 */
	private int getStringTagFieldAsInteger(Tag tag, FieldKey key, int defaultValue) {
		int value = defaultValue;
		
		String field = tag.getFirst(key);
		if (field != null) {
			try {
				value = Integer.parseInt(field);
			} catch (NumberFormatException e) {
				value = defaultValue;
			}
		}

		return value;
	}

	/**
	 * Write out the contents of this index file to the specified output folder based on the
	 * information accumulated by the files that were added along the way.
	 * 
	 * @param outputFolder
	 * @throws IOException
	 */
	private void writeIndexFile(File outputFolder) 
		throws IOException 
	{
		System.out.println("Writing index file with count: " + count);
		
		// Grab the data contents of the file
		dos.flush();
		byte[] bytes = bos.toByteArray();
		
		// Open up a new output file
		File outputFile = new File(outputFolder, "database_idx.tcd");
		FileOutputStream fos = new FileOutputStream(outputFile);
		dos = new MultiEndianDataOutputStream(fos);
	
		/* Write the header
		 
		Bytes	Content
		4	 Database version (first three bytes are TCH, last byte is the version)
		4	 Size (in bytes) of the non-header part of the file
		4	 Number of entries in the file
		4	 Serial (used for last played, see note)
		4	 Commit id (increments by 1 each commit)
		4	 Dirty (if true, db commit has failed and the db is broken)
		 */
		writeInt(VERSION);
		writeInt(bytes.length);
		writeInt(count);
		writeInt(0); // Serial
		writeInt(1); // Commit id
		writeInt(0); // Dirty
		
		// Write the data
		dos.write(bytes);
		dos.close();
	}

	/**
	 * Add the various String tag values to their tag files, accounting for the
	 * various oddities with the different files and their values.
	 * 
	 * @param diskname The "name" to be used for generation of the filename
	 * in the database.
	 * 
	 * @param rootFolder The local file system root folder for relative path calculation.
	 * 
	 * @param file The file path to be calculated
	 * 
	 * @throws IOException
	 */
	private void addStringTags(String diskname, File rootFolder, AudioFile file) 
		throws IOException 
	{
		Tag tag = file.getTag();
		for (int i = 0; i < TAG_KEYS.length; i++) {
			FieldKey fieldKey = TAG_KEYS[i];
			TagFile tagFile = tagFiles[i];
						
			String tagValue = null;
			if ((fieldKey != null) && (tagFile != null)) {
				switch (fieldKey) {
					case GENRE:
						tagValue = getGenre(tag, fieldKey);
						break;
					
					case ALBUM_ARTIST:
						// If there is no album artist, fall back to artist
						tagValue = tag.getFirst(fieldKey);
						if ((tagValue == null) || (tagValue.length() == 0)) {
							tagValue = tag.getFirst(FieldKey.ARTIST);
						}
						break;
						
					case GROUPING:
						// If there is no grouping, fall back to title
						tagValue = tag.getFirst(fieldKey);
						if ((tagValue == null) || (tagValue.length() == 0)) {
							tagValue = tag.getFirst(FieldKey.TITLE);
						}
						break;
						
					default: 
						// no special cases required
						tagValue = tag.getFirst(fieldKey);
						break;
				}
				
				// At the time of this writing, the wiki says that things that don't have
				// a value, get mapped to an empty string.  That appears to have changed
				// and is now mapped to "<Untagged>".
				if ((tagValue == null) || (tagValue.length() == 0)) tagValue = "<Untagged>";
				
			} else {
				// Handle the file name for this file
				tagValue = getMappedFilename(diskname, rootFolder, file);
			}
			
			// Title and filename pass the index of this entry for reverse
			// mapping.  Otherwise, just pass 0xFFFFFFFF when adding this
			// tag to the appropriate tag file.
			int masterIndex = ((i == 3) || (i == 4)) ? count : 0xFFFFFFFF;
			int tagOffset = tagFile.addTagValue(tagValue, masterIndex);
			
			writeInt(tagOffset);
		}
	}

	/**
	 * Write the final tag file contents for each tag file to
	 * the specified output folder.
	 * 
	 * @param outputFolder
	 * @throws IOException
	 */
	private void writeTagFiles(File outputFolder)
		throws IOException
	{
		for (int i = 0; i < tagFiles.length; i++) {
			File outputFile = new File(outputFolder, "database_" + i + ".tcd");
			tagFiles[i].writeFile(outputFile);
		}
	}
}
