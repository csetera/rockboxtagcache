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
package org.seterasoft.rockbox.tagcache.util;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * {@link DataOutputStream} subclass that adds support for little
 * endian integer outputs.  The ideal would have been the ability to 
 * override writeInt, however that is a final method, so instead, we
 * have added methods for little endian output.
 * 
 * @author Craig Setera
 */
public class MultiEndianDataOutputStream extends DataOutputStream {
	
	public MultiEndianDataOutputStream(OutputStream out) {
		super(out);
	}

    /**
     * Writes an <code>int</code> to the underlying output stream as four
     * bytes, low byte first. If no exception is thrown, the counter 
     * <code>written</code> is incremented by <code>4</code>.
     *
     * @param      v   an <code>int</code> to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     */
    public final void writeLittleEndianInt(int v) 
    	throws IOException 
    {
        write((v >>>  0) & 0xFF);
        write((v >>>  8) & 0xFF);
        write((v >>> 16) & 0xFF);
        write((v >>> 24) & 0xFF);
    }

    /**
     * Writes a <code>short</code> to the underlying output stream as two
     * bytes, low byte first. If no exception is thrown, the counter 
     * <code>written</code> is incremented by <code>2</code>.
     *
     * @param      v   a <code>short</code> to be written.
     * @exception  IOException  if an I/O error occurs.
     * @see        java.io.FilterOutputStream#out
     */
    public final void writeLittleEndianShort(int v) 
    	throws IOException 
    {
        write((v >>> 0) & 0xFF);
        write((v >>> 8) & 0xFF);
    }
}
