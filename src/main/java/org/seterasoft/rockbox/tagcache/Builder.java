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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.TagException;

/**
 * The main entry point for the Rockbox Tagcache builder functionality.
 * <br/>
 * All format information gleaned from http://www.rockbox.org/wiki/TagcacheDBFormat
 * and hard fought battles using binary editor.
 *
 * @author Craig Setera
 */
public class Builder {
	
	/*
	 * A holder class for error information that tracks exceptions that occur
	 * on a particular file, while allowing the rest of the files to be processed.
	 * These errors are displayed at the end of the execution along with the
	 * processed statistics.
	 */
	class FileError {
		Throwable t;
		File f;
		
		public FileError(File f, Throwable t) {
			super();
			this.f = f;
			this.t = t;
		}

		@Override
		public String toString() {
			return "Error " + t.getMessage() + " for file: " + f;

		}
	}
	
	private Configuration configuration;
	private IndexFile indexFile;
	private ArrayList<FileError> errors;

	/**
	 * Main entry point into the program.  Expects a single parameter pointing
	 * to a properties file containing the configuration for the tagcache building
	 * operation.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length == 0) {
			System.err.println("Usage: java " + Builder.class.getName() + " <configuration properties file>");
			return;
		}
		
		try {
			// Load the configuration from the specified configuration file
			Properties configProperties = new Properties();
			
			InputStream configPropertyStream = new FileInputStream(args[0]);
			if (configPropertyStream != null) {
				configProperties.load(configPropertyStream);
				configPropertyStream.close();
			}
			
			new Builder(configProperties).run();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Construct a new builder for the specified configuration properties.
	 * See {@link Configuration} for more information on the expected properties
	 * and their format.
	 * 
	 * @param configProperties
	 */
	public Builder(Properties configProperties) {
		this.configuration = new Configuration(configProperties);
		errors = new ArrayList<Builder.FileError>();
	}

	/**
	 * Run the tagcache build operation.  This will attempt to build a set of Rockbox tagcache files
	 * based on the configuration specified when constructing the builder object.
	 * 
	 * @throws CannotReadException
	 * @throws IOException
	 * @throws TagException
	 * @throws ReadOnlyFileException
	 * @throws InvalidAudioFrameException
	 */
	public void run() 
		throws CannotReadException, IOException, TagException, ReadOnlyFileException, InvalidAudioFrameException 
	{
		// The database_idx.tcd file
		this.indexFile = new IndexFile(configuration.isHostLittleEndian());
		
		// For each media directory, recursively work through the folders and files
		Map<String, File> mediaMappings = configuration.getMediaMappings();
		for (Map.Entry<String, File> entry : mediaMappings.entrySet()) {
			if (entry.getValue().isDirectory()) {
				String mediaName = entry.getKey();
				File localFolder = entry.getValue();
				
				if (localFolder.exists()) {
					processDirectoryContents(mediaName, localFolder, localFolder);
				}
			} else {
				errors.add(new FileError(
					entry.getValue(), 
					new FileNotFoundException("Folder not found: " + entry.getValue())));
			}
		}
		
		// Write out the resulting database files
		configuration.getRockboxDirectory().mkdirs();
		indexFile.writeFiles(configuration.getRockboxDirectory());
		
		// Let the user know if there were any errors along the way
		if (!errors.isEmpty()) {
			System.err.println("*** Errors during processing ***");
			for (FileError fe :errors) {
				System.err.println(fe);
			}
		}
	}

	/**
	 * Attempt to add a file to the tagcache.  The specified file will be checked
	 * against the allowed files regular expression and if it passes, the file
	 * tags will be used to populate an entry in the database.
	 * 
	 * @param diskname The "name" to be used for generation of the filename
	 * in the database.
	 * 
	 * @param rootFolder The local file system root folder for relative path calculation.
	 * 
	 * @param file The actual file to be added to the database.
	 */
	private void addFileToCache(String diskname, File rootFolder, File file) {
		// Check to see if this file should be included 
		Matcher matcher = configuration.getFileMatchExpression().matcher(file.getPath());
		if (matcher.matches()) {
			try {
				// Use the JAudioTagger library to handle tag parsing
				AudioFile audioFile = AudioFileIO.read(file);
				indexFile.addFile(diskname, rootFolder, audioFile);
			} catch (Exception e) {
				// Don't stop overall processing if we run into an issue with this particular file
				errors.add(new FileError(file, e));
			}
		}
	}

	/**
	 * Process the specified directory on disk, recursively digging further into the 
	 * folder structure to find potential files.
	 * 
	 * @param diskname The "name" to be used for generation of the filename
	 * in the database.
	 * 
	 * @param rootFolder The local file system root folder for relative path calculation.
	 * 
	 * @param currentFolder The folder to be processed recursively.
	 * 
	 * @throws CannotReadException
	 * @throws IOException
	 * @throws TagException
	 * @throws ReadOnlyFileException
	 * @throws InvalidAudioFrameException
	 */
	private void processDirectoryContents(String diskname, File rootFolder, File currentFolder) 	
		throws CannotReadException, IOException, TagException, ReadOnlyFileException, InvalidAudioFrameException 
	{		
		File[] files = currentFolder.listFiles();
		for (File file : files) {
			if (file.isDirectory()) {
				processDirectoryContents(diskname, rootFolder, file);
			} else {
				addFileToCache(diskname, rootFolder, file);
			}
		}
	}
}
