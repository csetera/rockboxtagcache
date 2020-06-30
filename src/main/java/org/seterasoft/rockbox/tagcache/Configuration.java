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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Wrapper class for the properties file being used to drive the generation
 * process.  This class expects to be configured with a {@link Properties} object
 * containing the following properties:
 * <table>
 * <tr><th>Property</th><th>Description</th><th>Example</th></tr>
 * 
 * <tr><td>host.endian</td>
 * <td>Set the proper endianness for the host machine, as the database is stored according to the host's endianness. 
 * Which is big endian for coldfire and SH1, and little endian for ARM.</td>
 * <td>host.endian=little</td></tr>
 * 
 * <tr><td>file.match.expression</td>
 * <td>Set an appropriate regular expression to match the types of
 * files you want added to the database. This expression should
 * be set up to match including the path.</td>
 * <td>file.match.expression=.*\\.mp3</td></tr>
 * 
 * <tr><td>dir.target</td>
 * <td>The folder for the database to be written.  This could be 
 * direct to the Rockbox (.rockbox) folder or an intermediate
 * folder.</td>
 * <td>dir.target=/home/user/temp/rockbox</td></tr>
 * 
 * <tr><td>media</td>
 * <td>Specify a comma-separated list of the available media.  As implemented,
 * it is assumed that there is only internal (mapped to root)
 * or external (mapped to <microSD1>) media.</td>
 * <td>media=internal,external</td></tr>
 * 
 * <tr><td>media.internal</td>
 * <td> Location (when mounted) to find the internal media
 * in the PC's file system.</td>
 * <td>media.internal=/media/FUZEV2</td></tr>
 * 
 * <tr><td>media.external</td>
 * <td> Optional location (when mounted) to find the external media
 * in the PC's file system.</td>
 * <td>media.external=/media/16G MICROSD</td></tr>
 * 
 * </table>
 * 
 * @author Craig Setera
 *
 */
public class Configuration {
	private Properties configProperties;
	private Pattern matchExpression;
	private Map<String, File> diskMappings;
	
	/** 
	 * Construct a new {@link Configuration} object based on the specified properties.
	 * 
	 * @param configProperties
	 */
	public Configuration(Properties configProperties) {
		this.configProperties = configProperties;
	}
	
	/**
	 * Return the mappings from available media to their location on the file system.
	 * 
	 * @return
	 */
	public Map<String, File> getMediaMappings() {
		if (diskMappings == null) {
			diskMappings = new HashMap<String, File>();
			
			String mediaProperty = configProperties.getProperty("media", "");
			String[] mediaStrings = mediaProperty.split(",");
			for (String media : mediaStrings) {				
				String devicePath = media.equals("internal") ? "" : "<microSD1>";
				String folderName = configProperties.getProperty("media." + media);
				
				if (folderName != null) {
					diskMappings.put(devicePath, new File(folderName));
				}
			}
		}
		
		return diskMappings;
	}
	
	public Pattern getFileMatchExpression() {
		if (matchExpression == null) {
			matchExpression = Pattern.compile(configProperties.getProperty("file.match.expression", ".*"));
		}
		
		return matchExpression;
	}
	
	public File getRockboxDirectory() {
		return new File(configProperties.getProperty("dir.rockbox", System.getProperty("user.dir") + ".rockbox"));
	}
	
	public boolean isHostLittleEndian() {
		return "little".equalsIgnoreCase(configProperties.getProperty("host.endian"));
	}
}
