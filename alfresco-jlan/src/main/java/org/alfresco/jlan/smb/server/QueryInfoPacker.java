/*
 * Copyright (C) 2006-2007 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */

package org.alfresco.jlan.smb.server;

import org.alfresco.jlan.server.filesys.FileInfo;
import org.alfresco.jlan.server.filesys.UnsupportedInfoLevelException;
import org.alfresco.jlan.smb.FileInfoLevel;
import org.alfresco.jlan.smb.NTTime;
import org.alfresco.jlan.smb.SMBDate;
import org.alfresco.jlan.smb.WinNT;
import org.alfresco.jlan.smb.server.ntfs.StreamInfo;
import org.alfresco.jlan.smb.server.ntfs.StreamInfoList;
import org.alfresco.jlan.util.DataBuffer;

/**
 * Query File Information Packer Class
 * 
 * <p>Packs file/directory information for the specified information level.
 *
 * @author gkspencer
 */
public class QueryInfoPacker {

  /**
   * Pack a file information object into the specified buffer, using the specified information level.
   *
   * @param info        File information to be packed.
   * @param buf         Buffer to pack the data into.
   * @param infoLevel   File information level.
   * @param uni					Pack Unicode strings if true, else pack ASCII strings
   * @return int        Length of data packed
   */
  public final static int packInfo(FileInfo info, DataBuffer buf, int infoLevel, boolean uni)
    throws UnsupportedInfoLevelException {

    //  Determine the information level

    int curPos = buf.getPosition();

    switch (infoLevel) {

      //  Standard information

      case FileInfoLevel.PathStandard:
        packInfoStandard(info, buf, false, uni);
        break;
        
      //	Standard information plus EA size
      
      case FileInfoLevel.PathQueryEASize:
        packInfoStandard(info, buf, true, uni);
        break;
        
      //	Extended attributes list
      
      case FileInfoLevel.PathQueryEAsFromList:
      	break;
      	
      //	All extended attributes
      
      case FileInfoLevel.PathAllEAs:
        packAllEAsInfo(info, buf);
        break;
      	
      //	Validate a file name
      
      case FileInfoLevel.PathIsNameValid:
      	break;
      	
      //	Basic file information
      
      case FileInfoLevel.PathFileBasicInfo:
      case FileInfoLevel.NTFileBasicInfo:
      	packBasicFileInfo(info, buf);
      	break;
      	
      //	Standard file information
      
      case FileInfoLevel.PathFileStandardInfo:
      case FileInfoLevel.NTFileStandardInfo:
      	packStandardFileInfo(info, buf);
      	break;
      	
      //	Extended attribute information
      
      case FileInfoLevel.PathFileEAInfo:
      case FileInfoLevel.NTFileEAInfo:
      	packEAFileInfo(info,buf);
      	break;
      	
      //	File name information
      
      case FileInfoLevel.PathFileNameInfo:
      case FileInfoLevel.NTFileNameInfo:
      	packNameFileInfo(info,buf,uni);
      	break;
      	
      //	All information
      
      case FileInfoLevel.PathFileAllInfo:
      case FileInfoLevel.NTFileAllInfo:
      	packAllFileInfo(info,buf,uni);
      	break;
      	
      //	Alternate name information
      
      case FileInfoLevel.PathFileAltNameInfo:
      case FileInfoLevel.NTFileAltNameInfo:
      	packAlternateNameFileInfo(info,buf);
      	break;
      	
      //	Stream information
      
      case FileInfoLevel.PathFileStreamInfo:
      case FileInfoLevel.NTFileStreamInfo:
      	packStreamFileInfo(info,buf,uni);
      	break;
      	
      //	Compression information
      
      case FileInfoLevel.PathFileCompressionInfo:
      case FileInfoLevel.NTFileCompressionInfo:
      	packCompressionFileInfo(info,buf);
      	break;
      	
      //	File internal information
      
      case FileInfoLevel.NTFileInternalInfo:
      	packFileInternalInfo(info,buf);
      	break;
      	
      //	File position information
      
      case FileInfoLevel.NTFilePositionInfo:
      	packFilePositionInfo(info,buf);
      	break;
      	
      //	Attribute tag information
      
      case FileInfoLevel.NTAttributeTagInfo:
      	packFileAttributeTagInfo(info,buf);
      	break;
      	
      //	Network open information
      
      case FileInfoLevel.NTNetworkOpenInfo:
      	packFileNetworkOpenInfo(info,buf);
      	break;
    }
    
    //	Return the length of the data that was packed
    
    return buf.getPosition() - curPos;
  }

	/**
	 * Pack the standard file information
	 * 
	 * @param info		File information
	 * @param buf			Buffer to pack data into
	 * @param eaFlag	Return EA size
	 * @param uni			Pack unicode strings
	 */
	private static void packInfoStandard(FileInfo info, DataBuffer buf, boolean eaFlag, boolean uni) {

    //  Information format :-
    //    SMB_DATE   CreationDate
    //    SMB_TIME  CreationTime
    //    SMB_DATE  LastAccessDate
    //    SMB_TIME  LastAccessTime
    //    SMB_DATE  LastWriteDate
    //    SMB_TIME  LastWriteTime
    //    ULONG      File size
    //    ULONG      Allocation size
    //    USHORT    File attributes
    //  [  ULONG      EA size ]

    //  Pack the creation date/time

		SMBDate dateTime = new SMBDate(0);
		
    if (info.hasCreationDateTime()) {
      dateTime.setTime(info.getCreationDateTime());
      buf.putShort(dateTime.asSMBDate());
      buf.putShort(dateTime.asSMBTime());
    }
    else
    	buf.putZeros(4);

    //  Pack the last access date/time

    if (info.hasAccessDateTime()) {
      dateTime.setTime(info.getAccessDateTime());
      buf.putShort(dateTime.asSMBDate());
      buf.putShort(dateTime.asSMBTime());
    }
    else
    	buf.putZeros(4);

    //  Pack the last write date/time

    if (info.hasModifyDateTime()) {
      dateTime.setTime(info.getModifyDateTime());
      buf.putShort(dateTime.asSMBDate());
      buf.putShort(dateTime.asSMBTime());
    }
    else
    	buf.putZeros(4);

    //  Pack the file size and allocation size

		buf.putInt(info.getSizeInt());

    if (info.getAllocationSize() < info.getSize())
    	buf.putInt(info.getSizeInt());
    else
    	buf.putInt(info.getAllocationSizeInt());

    //  Pack the file attributes

		buf.putShort(info.getFileAttributes());

    //  Pack the EA size, always 4.

    if (eaFlag == true)
    	buf.putInt(4);
	}
	
	/**
	 * Pack the basic file information (level 0x101)
	 * 
	 * @param info		File information
	 * @param buf			Buffer to pack data into
	 */
	private static void packBasicFileInfo(FileInfo info, DataBuffer buf) {

    //  Information format :-
    //		LARGE_INTEGER	Creation date/time
    //		LARGE_INTEGER	Access date/time
    //		LARGE_INTEGER	Write date/time
    //		LARGE_INTEGER	Change date/time
    //		UINT		Attributes
    //		UINT		Unknown

    //  Pack the creation date/time

    if (info.hasCreationDateTime()) {
      buf.putLong(NTTime.toNTTime(info.getCreationDateTime()));
    }
    else
    	buf.putZeros(8);

    //  Pack the last access date/time

    if (info.hasAccessDateTime()) {
      buf.putLong(NTTime.toNTTime(info.getAccessDateTime()));
    }
    else if ( info.hasModifyDateTime())
      buf.putLong(NTTime.toNTTime(info.getModifyDateTime()));
    else
    	buf.putZeros(8);

    //  Pack the last write and change date/time

    if (info.hasModifyDateTime()) {
      long ntTime = NTTime.toNTTime(info.getModifyDateTime());
      buf.putLong(ntTime);
      buf.putLong(ntTime);
    }
    else
    	buf.putZeros(16);

    //  Pack the file attributes

		buf.putInt(info.getFileAttributes());

		//	Pack unknown value

		buf.putZeros(4);		
	}

	/**
	 * Pack the standard file information (level 0x102)
	 * 
	 * @param info		File information
	 * @param buf			Buffer to pack data into
	 */
	private static void packStandardFileInfo(FileInfo info, DataBuffer buf) {

    //  Information format :-
    //		LARGE_INTEGER	AllocationSize
    //		LARGE_INTEGER	EndOfFile
    //		UINT NumberOfLinks
    //		BOOLEAN DeletePending
    //		BOOLEAN Directory
	  //		SHORT Unknown

    //  Pack the allocation and file sizes

    if (info.getAllocationSize() < info.getSize())
    	buf.putLong(info.getSize());
    else
    	buf.putLong(info.getAllocationSize());

		buf.putLong(info.getSize());    
    
    //	Pack the number of links, always one for now

		buf.putInt(1);
    
    //	Pack the delete pending and directory flags

		buf.putByte(0);
		buf.putByte(info.isDirectory() ? 1 : 0);
		
//		buf.putZeros(2);
	}

	/**
	 * Pack the extended attribute information (level 0x103)
	 * 
	 * @param info		File information
	 * @param buf			Buffer to pack data into
	 */
	private static void packEAFileInfo(FileInfo info, DataBuffer buf) {

    //  Information format :-
    //		ULONG EASize

    //  Pack the extended attribute size

		buf.putInt(0);
	}

	/**
	 * Pack the file name information (level 0x104)
	 * 
	 * @param info		File information
	 * @param buf			Buffer to pack data into
	 * @param uni			Pack unicode strings
	 */
	private static void packNameFileInfo(FileInfo info, DataBuffer buf, boolean uni) {

    //  Information format :-
    //		UINT FileNameLength
    //		WCHAR FileName[]

		//	Pack the file name length and name string as Unicode

		int nameLen = info.getFileName().length();
		if ( uni)
			nameLen *= 2;
			
		buf.putInt(nameLen);
		buf.putString(info.getFileName(), uni, false);
	}

	/**
	 * Pack the all file information (level 0x107)
	 * 
	 * @param info		File information
	 * @param buf			Buffer to pack data into
	 * @param uni			Pack unicode strings
	 */
	private static void packAllFileInfo(FileInfo info, DataBuffer buf, boolean uni) {

	  //  Information format :-
    //		LARGE_INTEGER	Creation date/time
    //		LARGE_INTEGER	Access date/time
    //		LARGE_INTEGER	Write date/time
    //		LARGE_INTEGER	Change date/time
    //		UINT		Attributes
    //		UINT		Number of links
    //		LARGE_INTEGER Allocation
    //		LARGE_INTEGER Size
	  //		BYTE 		Delete pending
	  //		BYTE		Directory flag
	  //		2 byte longword alignment
	  //		UINT		EA Size
	  //		UINT		Access mask
	  //		UINT		File name length
	  //		WCHAR		FileName[]

    //  Pack the creation date/time

    if (info.hasCreationDateTime()) {
      buf.putLong(NTTime.toNTTime(info.getCreationDateTime()));
    }
    else
    	buf.putZeros(8);

    //  Pack the last access date/time

    if (info.hasAccessDateTime()) {
      buf.putLong(NTTime.toNTTime(info.getAccessDateTime()));
    }
    else
    	buf.putZeros(8);

    //  Pack the last write and change date/time

    if (info.hasModifyDateTime()) {
      long ntTime = NTTime.toNTTime(info.getModifyDateTime());
      buf.putLong(ntTime);
      buf.putLong(ntTime);
    }
    else
    	buf.putZeros(16);

    //  Pack the file attributes

		buf.putInt(info.getFileAttributes());

		//	Number of links

		buf.putInt(1);
		
		//	Pack the allocation and used file sizes
		
    if (info.getAllocationSize() < info.getSize())
    	buf.putLong(info.getSize());
    else
    	buf.putLong(info.getAllocationSize());

		buf.putLong(info.getSize());    
    
    //	Pack the delete pending and directory flags

		buf.putByte(0);
		buf.putByte(info.isDirectory() ? 1 : 0);
		buf.putShort(0);		//	Alignment

		//	EA list size, always 4
		
		buf.putInt(4);
		
		//	Access mask

		buf.putInt(0x00000003);
		
		//	File name length in bytes and file name, Unicode
		
		int nameLen = info.getFileName().length();
		if ( uni)
			nameLen *= 2;
			
		buf.putInt(nameLen);
		buf.putString(info.getFileName(), uni, false);
	}

	/**
	 * Pack the alternate name information (level 0x108)
	 * 
	 * @param info		File information
	 * @param buf			Buffer to pack data into
	 */
	private static void packAlternateNameFileInfo(FileInfo info, DataBuffer buf) {
	}

	/**
	 * Pack the stream information (level 0x109)
	 * 
	 * @param info		File information
	 * @param buf			Buffer to pack data into
	 * @param uni			Pack unicode strings
	 */
	private static void packStreamFileInfo(FileInfo info, DataBuffer buf, boolean uni) {
		
    //  Information format :-
    //		ULONG OffsetToNextStreamInfo
    //		ULONG NameLength (in bytes)
    //		LARGE_INTEGER StreamSize
    //		LARGE_INTEGER StreamAlloc
    //		WCHAR StreamName[]
    
    //	Pack a dummy data stream for now

		String streamName = "::$DATA";

		buf.putInt(0);		    //	offset to next info (no more info)
		
		int nameLen = streamName.length();
		if ( uni)
			nameLen *= 2;
		buf.putInt(nameLen);			
    
    //	Stream size
    
		buf.putLong(info.getSize());

		//	Allocation size
		
    if (info.getAllocationSize() < info.getSize())
    	buf.putLong(info.getSize());
    else
    	buf.putLong(info.getAllocationSize());

		buf.putString(streamName, uni, false);
	}

	/**
	 * Pack the stream information (level 0x109)
	 * 
	 * @param streams	List of streams
	 * @param buf			Buffer to pack data into
	 * @param uni			Pack unicode strings
	 * @return int
	 */
	public static int packStreamFileInfo(StreamInfoList streams, DataBuffer buf, boolean uni) {
		
		//  Information format :-
		//		ULONG OffsetToNextStreamInfo
		//		ULONG NameLength (in bytes)
		//		LARGE_INTEGER StreamSize
		//		LARGE_INTEGER StreamAlloc
		//		WCHAR StreamName[]
    
    //	Loop through the available streams

		int curPos = buf.getPosition();
		int startPos = curPos;
		int pos = 0;
		    
    for ( int i = 0; i < streams.numberOfStreams(); i++) {
    	
    	//	Get the current stream information
    	
    	StreamInfo sinfo = streams.getStreamAt(i);

			//	Skip the offset to the next stream information structure
			
			buf.putInt(0);
			
			//	Set the stream name length
			    	
			int nameLen = sinfo.getName().length();
			if ( uni)
				nameLen *= 2;
			buf.putInt(nameLen);			
    
			//	Stream size
    
			buf.putLong(sinfo.getSize());

			//	Allocation size
		
			if (sinfo.getAllocationSize() < sinfo.getSize())
				buf.putLong(sinfo.getSize());
			else
				buf.putLong(sinfo.getAllocationSize());

			buf.putString(sinfo.getName(), uni, false);
			
			//	Word align the buffer
			
			buf.wordAlign();
			
			//	Fill in the offset to the next stream information, if this is not the last stream

			if ( i < (streams.numberOfStreams() - 1)) {
				
				//	Fill in the offset from the current stream information structure to the next

				pos = buf.getPosition();			
				buf.setPosition(startPos);
				buf.putInt(pos - startPos);
				buf.setPosition(pos);
				startPos = pos;
			}
    }
    
    //	Return the data length
    
    return buf.getPosition() - curPos;
	}

	/**
	 * Pack the compression information (level 0x10B)
	 * 
	 * @param info		File information
	 * @param buf			Buffer to pack data into
	 */
	private static void packCompressionFileInfo(FileInfo info, DataBuffer buf) {

    //  Information format :-
    //		LARGE_INTEGER CompressedSize
    //		ULONG CompressionFormat (sess WinNT class)

		buf.putLong(info.getSize());
		buf.putInt(WinNT.CompressionFormatNone);
	}

	/**
	 * Pack the file internal information (level 1006)
	 * 
	 * @param info		File information
	 * @param buf			Buffer to pack data into
	 */
	private static void packFileInternalInfo(FileInfo info, DataBuffer buf) {

    //  Information format :-
    //		ULONG Unknown1
    //		ULONG Unknown2

		buf.putInt(1);
		buf.putInt(0);
	}

	/**
	 * Pack the file position information (level 1014)
	 * 
	 * @param info		File information
	 * @param buf			Buffer to pack data into
	 */
	private static void packFilePositionInfo(FileInfo info, DataBuffer buf) {

    //  Information format :-
    //		ULONG Unknown1
    //		ULONG Unknown2

		buf.putInt(0);
		buf.putInt(0);
	}

	/**
	 * Pack the network open information (level 1034)
	 * 
	 * @param info		File information
	 * @param buf			Buffer to pack data into
	 */
	private static void packFileNetworkOpenInfo(FileInfo info, DataBuffer buf) {

    //  Information format :-
    //		LARGE_INTEGER	Creation date/time
    //		LARGE_INTEGER	Access date/time
    //		LARGE_INTEGER	Write date/time
    //		LARGE_INTEGER	Change date/time
    //		LARGE_INTEGER Allocation
    //		LARGE_INTEGER Size
    //		UINT		Attributes
    //		UNIT		Unknown

    //  Pack the creation date/time

    if (info.hasCreationDateTime()) {
      buf.putLong(NTTime.toNTTime(info.getCreationDateTime()));
    }
    else
    	buf.putZeros(8);

    //  Pack the last access date/time

    if (info.hasAccessDateTime()) {
      buf.putLong(NTTime.toNTTime(info.getAccessDateTime()));
    }
    else
    	buf.putZeros(8);

    //  Pack the last write and change date/time

    if (info.hasModifyDateTime()) {
      long ntTime = NTTime.toNTTime(info.getModifyDateTime());
      buf.putLong(ntTime);
      buf.putLong(ntTime);
    }
    else
    	buf.putZeros(16);

		//	Pack the allocation and used file sizes
		
    if (info.getAllocationSize() < info.getSize())
    	buf.putLong(info.getSize());
    else
    	buf.putLong(info.getAllocationSize());

		buf.putLong(info.getSize());    
    
    //  Pack the file attributes

		buf.putInt(info.getFileAttributes());

		//	Pack the unknown value

		buf.putInt(0);		
	}

	/**
	 * Pack the attribute tag information (level 1035)
	 * 
	 * @param info		File information
	 * @param buf			Buffer to pack data into
	 */
	private static void packFileAttributeTagInfo(FileInfo info, DataBuffer buf) {

    //  Information format :-
    //		ULONG Unknown1
    //		ULONG Unknown2

		buf.putLong(0);
		buf.putLong(0);
	}
  
  /**
   * Pack the extended attributes (level 1035)
   * 
   * @param info File information
   * @param buf Buffer to pack data into
   */
  private static void packAllEAsInfo(FileInfo info, DataBuffer buf) {
    // Information format :-
    // UINT EA list length

    buf.putInt( 4);
  }
  
}
