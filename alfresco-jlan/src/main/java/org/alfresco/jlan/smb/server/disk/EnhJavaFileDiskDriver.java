/*
 * Copyright (C) 2006-2008 Alfresco Software Limited.
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
package org.alfresco.jlan.smb.server.disk;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.StringTokenizer;

import org.alfresco.jlan.debug.Debug;
import org.alfresco.jlan.server.SrvSession;
import org.alfresco.jlan.server.core.DeviceContext;
import org.alfresco.jlan.server.core.DeviceContextException;
import org.alfresco.jlan.server.filesys.AccessDeniedException;
import org.alfresco.jlan.server.filesys.DiskDeviceContext;
import org.alfresco.jlan.server.filesys.DiskInterface;
import org.alfresco.jlan.server.filesys.FileAttribute;
import org.alfresco.jlan.server.filesys.FileExistsException;
import org.alfresco.jlan.server.filesys.FileInfo;
import org.alfresco.jlan.server.filesys.FileName;
import org.alfresco.jlan.server.filesys.FileOpenParams;
import org.alfresco.jlan.server.filesys.FileStatus;
import org.alfresco.jlan.server.filesys.FileSystem;
import org.alfresco.jlan.server.filesys.NetworkFile;
import org.alfresco.jlan.server.filesys.PathNotFoundException;
import org.alfresco.jlan.server.filesys.SearchContext;
import org.alfresco.jlan.server.filesys.TreeConnection;
import org.alfresco.jlan.server.locking.FileLockingInterface;
import org.alfresco.jlan.server.locking.LockManager;
import org.alfresco.jlan.smb.server.SMBSrvSession;
import org.alfresco.config.ConfigElement;

/**
 * Disk interface implementation that uses the java.io.File class.
 *
 * @author gkspencer
 */
public class EnhJavaFileDiskDriver implements DiskInterface, FileLockingInterface {

  //	DOS file seperator character
    private static final String DOS_SEPERATOR = "\\";

	//	SMB date used as the creation date/time for all files
    protected static long _globalCreateDate = System.currentTimeMillis();

	//	Lock manager
    private static LockManager _lockManager = new NIOLockManager();

    /**
     * Class constructor
     */
    public EnhJavaFileDiskDriver() {
        super();
    }

    /**
     * Build the file information for the specified file/directory, if it
     * exists.
     *
     * @param path String
     * @param relPath String
     * @return FileInfo
     */
    protected FileInfo buildFileInformation(String path, String relPath) {

    //  Now split the path up again !
        String[] pathStr = FileName.splitPath(path, java.io.File.separatorChar);

    //  Create a Java file to get the file/directory information
        if (pathStr[1] != null) {

      //  Create a file object
            File file = new File(pathStr[0], pathStr[1]);
            if (file.exists() == true && file.isFile()) {

        //  Fill in a file information object for this file/directory
                long flen = file.length();
                long alloc = (flen + 512L) & 0xFFFFFFFFFFFFFE00L;
                int fattr = 0;

                if (file.isDirectory()) {
                    fattr = FileAttribute.Directory;
                }

                if (file.canWrite() == false) {
                    fattr += FileAttribute.ReadOnly;
                }

        //	Check for common hidden files
                if (pathStr[1].equalsIgnoreCase("Desktop.ini")
                        || pathStr[1].equalsIgnoreCase("Thumbs.db")
                        || pathStr[1].charAt(0) == '.') {
                    fattr += FileAttribute.Hidden;
                }

				//	Create the file information
                FileInfo finfo = new FileInfo(pathStr[1], flen, fattr);
                long fdate = file.lastModified();
                finfo.setModifyDateTime(fdate);
                finfo.setAllocationSize(alloc);
                finfo.setFileId(relPath.hashCode());

                finfo.setCreationDateTime(getGlobalCreateDateTime() > fdate ? fdate : getGlobalCreateDateTime());
                finfo.setChangeDateTime(fdate);

                return finfo;
            } else {
        //  Rebuild the path, looks like it is a directory
                File dir = new File(FileName.buildPath(pathStr[0], pathStr[1], null, java.io.File.separatorChar));
                if (dir.exists() == true) {
          //  Fill in a file information object for this directory
                    int fattr = 0;
                    if (dir.isDirectory()) {
                        fattr = FileAttribute.Directory;
                    }
                    FileInfo finfo = new FileInfo(pathStr[1] != null ? pathStr[1] : "", 0, fattr);
                    long fdate = file.lastModified();
                    finfo.setModifyDateTime(fdate);
                    finfo.setFileId(relPath.hashCode());

                    finfo.setCreationDateTime(getGlobalCreateDateTime() > fdate ? fdate : getGlobalCreateDateTime());
                    finfo.setChangeDateTime(fdate);

                    return finfo;
                }
            }
        } else {

      //  Get file information for a directory
            File dir = new File(pathStr[0]);
            if (dir.exists() == true) {

        //  Fill in a file information object for this directory
                int fattr = 0;
                if (dir.isDirectory()) {
                    fattr = FileAttribute.Directory;
                }

                FileInfo finfo = new FileInfo(pathStr[1] != null ? pathStr[1] : "", 0, fattr);
                long fdate = dir.lastModified();
                finfo.setModifyDateTime(fdate);
                finfo.setFileId(relPath.hashCode());

                finfo.setCreationDateTime(getGlobalCreateDateTime() > fdate ? fdate : getGlobalCreateDateTime());
                finfo.setChangeDateTime(fdate);

                return finfo;
            }
        }

    //  Bad path
        return null;
    }

    /**
     * Close the specified file
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param file	Network file details
     * @exception IOException
     */
    public void closeFile(SrvSession sess, TreeConnection tree, NetworkFile file)
            throws java.io.IOException {

    //	Close the file
        file.closeFile();
    //	Check if the file/directory is marked for delete
        if (file.hasDeleteOnClose()) {

  		//	Check for a file or directory
            if (file.isDirectory()) {
                deleteDirectory(sess, tree, file.getFullName());
            } else {
                deleteFile(sess, tree, file.getFullName());
            }
        }
    }

    /**
     * Create a new directory
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param params Directory parameters
     * @exception IOException
     */
    public void createDirectory(SrvSession sess, TreeConnection tree, FileOpenParams params)
            throws java.io.IOException {
    //  Get the full path for the new directory
        String dirname = FileName.buildPath(tree.getContext().getDeviceName(), params.getPath(), null, java.io.File.separatorChar);
    //  Create the new directory
        File newDir = new File(dirname);
        if (newDir.mkdir() == false) {
            throw new IOException("Failed to create directory " + dirname);
        }
    }

    /**
     * Create a new file
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param params	File open parameters
     * @return NetworkFile
     * @exception IOException
     */
    public NetworkFile createFile(SrvSession sess, TreeConnection tree, FileOpenParams params)
            throws java.io.IOException {

    //  Get the full path for the new file
        DeviceContext ctx = tree.getContext();
        String fname = FileName.buildPath(ctx.getDeviceName(), params.getPath(), null, java.io.File.separatorChar);

    //  Check if the file already exists
        File file = new File(fname);
        if (file.exists()) {
            throw new FileExistsException();
        }

    //  Create the new file
        FileWriter newFile = new FileWriter(fname, false);
        newFile.close();

    //  Create a Java network file
        file = new File(fname);
        NIOJavaNetworkFile netFile = new NIOJavaNetworkFile(file, params.getPath());
        netFile.setGrantedAccess(NetworkFile.READWRITE);
        netFile.setFullName(params.getPath());

    //  Return the network file
        return netFile;
    }

    /**
     * Delete a directory
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param dir	Path of directory to delete
     * @exception IOException
     */
    public void deleteDirectory(SrvSession sess, TreeConnection tree, String dir)
            throws java.io.IOException {

    //  Get the full path for the directory
        DeviceContext ctx = tree.getContext();
        String dirname = FileName.buildPath(ctx.getDeviceName(), dir, null, java.io.File.separatorChar);

    //  Check if the directory exists, and it is a directory
        File delDir = new File(dirname);
        if (delDir.exists() && delDir.isDirectory()) {

    	//	Check if the directory contains any files
            String[] fileList = delDir.list();
            if (fileList != null && fileList.length > 0) {
                throw new AccessDeniedException("Directory not empty");
            }

			//	Delete the directory
            delDir.delete();
        } //  If the path does not exist then try and map it to a real path, there may be case differences
        else if (delDir.exists() == false) {

      //  Map the path to a real path
            String mappedPath = mapPath(ctx.getDeviceName(), dir);
            if (mappedPath != null) {

        //  Check if the path is a directory
                delDir = new File(mappedPath);
                if (delDir.isDirectory()) {

        	//	Check if the directory contains any files
                    String[] fileList = delDir.list();
                    if (fileList != null && fileList.length > 0) {
                        throw new AccessDeniedException("Directory not empty");
                    }

        	//	Delete the directory
                    delDir.delete();
                }
            }
        }
    }

    /**
     * Delete a file
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param name	Name of file to delete
     * @exception IOException
     */
    public void deleteFile(SrvSession sess, TreeConnection tree, String name)
            throws java.io.IOException {

    //  Get the full path for the file
        DeviceContext ctx = tree.getContext();
        String fullname = FileName.buildPath(ctx.getDeviceName(), name, null, java.io.File.separatorChar);

    //  Check if the file exists, and it is a file
        File delFile = new File(fullname);
        if (delFile.exists() && delFile.isFile()) {
            delFile.delete();
        } //  If the path does not exist then try and map it to a real path, there may be case differences
        else if (delFile.exists() == false) {

      //  Map the path to a real path
            String mappedPath = mapPath(ctx.getDeviceName(), name);
            if (mappedPath != null) {

        //  Check if the path is a file and exists
                delFile = new File(mappedPath);
                if (delFile.exists() && delFile.isFile()) {
                    delFile.delete();
                }
            }
        }
    }

    /**
     * Check if the specified file exists, and it is a file.
     *
     * @param sess Session details
     * @param tree	Tree connection
     * @param name	File name
     * @return int
     */
    public int fileExists(SrvSession sess, TreeConnection tree, String name) {

    //  Get the full path for the file
        DeviceContext ctx = tree.getContext();
        String filename = FileName.buildPath(ctx.getDeviceName(), name, null, java.io.File.separatorChar);

    //  Check if the file exists, and it is a file
        File chkFile = new File(filename);
        if (chkFile.exists()) {

    	//	Check if the path is a file or directory
            if (chkFile.isFile()) {
                return FileStatus.FileExists;
            } else {
                return FileStatus.DirectoryExists;
            }
        }

    //  If the path does not exist then try and map it to a real path, there may be case differences
        if (chkFile.exists() == false) {

      //  Map the path to a real path
            try {
                String mappedPath = mapPath(ctx.getDeviceName(), name);
                if (mappedPath != null) {

          //  Check if the path is a file
                    chkFile = new File(mappedPath);
                    if (chkFile.exists()) {
                        if (chkFile.isFile()) {
                            return FileStatus.FileExists;
                        } else {
                            return FileStatus.DirectoryExists;
                        }
                    }
                }
            } catch (FileNotFoundException ex) {
            } catch (PathNotFoundException ex) {
            }
        }

    //  Path does not exist or is not a file
        return FileStatus.NotExist;
    }

    /**
     * Flush buffered data for the specified file
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param file	Network file
     * @exception IOException
     */
    public void flushFile(SrvSession sess, TreeConnection tree, NetworkFile file)
            throws java.io.IOException {

    //	Flush the file
        file.flushFile();
    }

    /**
     * Return file information about the specified file
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param name	File name
     * @return SMBFileInfo
     * @exception IOException
     */
    public FileInfo getFileInformation(SrvSession sess, TreeConnection tree, String name)
            throws java.io.IOException {

    //  Get the full path for the file/directory
        DeviceContext ctx = tree.getContext();
        String path = FileName.buildPath(ctx.getDeviceName(), name, null, java.io.File.separatorChar);

    //  Build the file information for the file/directory
        FileInfo info = buildFileInformation(path, name);

        if (info != null) {
            return info;
        }

    //  Try and map the path to a real path
        String mappedPath = mapPath(ctx.getDeviceName(), name);
        if (mappedPath != null) {
            return buildFileInformation(mappedPath, name);
        }

    //  Looks like a bad path
        return null;
    }

    /**
     * Determine if the disk device is read-only.
     *
     * @param sess	Session details
     * @param ctx	Device context
     * @return true if the device is read-only, else false
     * @exception IOException If an error occurs.
     */
    public boolean isReadOnly(SrvSession sess, DeviceContext ctx)
            throws java.io.IOException {

    //  Check if the directory exists, and it is a directory
        File rootDir = new File(ctx.getDeviceName());
        if (rootDir.exists() == false || rootDir.isDirectory() == false) {
            throw new FileNotFoundException(ctx.getDeviceName());
        }

    //  Create a temporary file in the root directory, this will test if we have write access
        //  to the shared directory.
        boolean readOnly = true;

        try {

      //  Create a temporary file
            File tempFile = null;
            boolean fileOK = false;

            while (fileOK == false) {

        //  Create a temporary file name
                tempFile = new File(rootDir, "_JSRV" + (System.currentTimeMillis() & 0x0FFF) + ".TMP");
                if (tempFile.exists() == false) {
                    fileOK = true;
                }
            }

      //  Create a temporary file
            FileWriter outFile = new FileWriter(tempFile);
            outFile.close();

      //  Delete the temporary file
            tempFile.delete();

      //  Shared directory appears to be writeable by the JVM
            readOnly = false;
        } catch (IllegalArgumentException ex) {
        } catch (IOException ex) {
        }

    //  Return the shared directory read-onyl status
        return readOnly;
    }

    /**
     * Map the input path to a real path, this may require changing the case of
     * various parts of the path.
     *
     * @param path Share relative path
     * @return Real path on the local filesystem
     */
    protected final String mapPath(String path)
            throws java.io.FileNotFoundException, PathNotFoundException {
        return mapPath("", path);
    }

    /**
     * Map the input path to a real path, this may require changing the case of
     * various parts of the path. The base path is not checked, it is assumed to
     * exist.
     *
     * @param base java.lang.String
     * @param path java.lang.String
     * @return java.lang.String
     * @exception java.io.FileNotFoundException The path could not be mapped to
     * a real path.
     * @exception PathNotFoundException	Part of the path is not valid
     */
    protected final String mapPath(String base, String path)
            throws java.io.FileNotFoundException, PathNotFoundException {

    //  Split the path string into seperate directory components
        String pathCopy = path;
        if (pathCopy.length() > 0 && pathCopy.startsWith(DOS_SEPERATOR)) {
            pathCopy = pathCopy.substring(1);
        }

        StringTokenizer token = new StringTokenizer(pathCopy, "\\/");
        int tokCnt = token.countTokens();

    //  The mapped path string, if it can be mapped
        String mappedPath = null;

        if (tokCnt > 0) {

      //  Allocate an array to hold the directory names
            String[] dirs = new String[token.countTokens()];

      //  Get the directory names
            int idx = 0;
            while (token.hasMoreTokens()) {
                dirs[idx++] = token.nextToken();
            }

      //  Check if the path ends with a directory or file name, ie. has a trailing '\' or not
            int maxDir = dirs.length;

            if (path.endsWith(DOS_SEPERATOR) == false) {

        //  Ignore the last token as it is a file name
                maxDir--;
            }

      //  Build up the path string and validate that the path exists at each stage.
            StringBuffer pathStr = new StringBuffer(base);
            if (base.endsWith(java.io.File.separator) == false) {
                pathStr.append(java.io.File.separator);
            }

            int lastPos = pathStr.length();
            idx = 0;
            File lastDir = null;
            if (base != null && base.length() > 0) {
                lastDir = new File(base);
            }
            File curDir = null;

            while (idx < maxDir) {

        //  Append the current directory to the path
                pathStr.append(dirs[idx]);
                pathStr.append(java.io.File.separator);

        //  Check if the current path exists
                curDir = new File(pathStr.toString());

                if (curDir.exists() == false) {

          //  Check if there is a previous directory to search
                    if (lastDir == null) {
                        throw new PathNotFoundException();
                    }

          //  Search the current path for a matching directory, the case may be different
                    String[] fileList = lastDir.list();
                    if (fileList == null || fileList.length == 0) {
                        throw new PathNotFoundException();
                    }

                    int fidx = 0;
                    boolean foundPath = false;

                    while (fidx < fileList.length && foundPath == false) {

            //  Check if the current file name matches the required directory name
                        if (fileList[fidx].equalsIgnoreCase(dirs[idx])) {

              //  Use the current directory name
                            pathStr.setLength(lastPos);
                            pathStr.append(fileList[fidx]);
                            pathStr.append(java.io.File.separator);

              //  Check if the path is valid
                            curDir = new File(pathStr.toString());
                            if (curDir.exists()) {
                                foundPath = true;
                                break;
                            }
                        }

            //  Update the file name index
                        fidx++;
                    }

          //  Check if we found the required directory
                    if (foundPath == false) {
                        throw new PathNotFoundException();
                    }
                }

        //  Set the last valid directory file
                lastDir = curDir;

        //  Update the end of valid path location
                lastPos = pathStr.length();

        //  Update the current directory index
                idx++;
            }

      //  Check if there is a file name to be added to the mapped path
            if (path.endsWith(DOS_SEPERATOR) == false) {

        //  Map the file name
                String[] fileList = lastDir.list();
                String fileName = dirs[dirs.length - 1];

        //	Check if the file list is valid, if not then the path is not valid
                if (fileList == null) {
                    throw new FileNotFoundException(path);
                }

        //	Search for the required file
                idx = 0;
                boolean foundFile = false;

                while (idx < fileList.length && foundFile == false) {
                    if (fileList[idx].compareTo(fileName) == 0) {
                        foundFile = true;
                    } else {
                        idx++;
                    }
                }

        //  Check if we found the file name, if not then do a case insensitive search
                if (foundFile == false) {

          //  Search again using a case insensitive search
                    idx = 0;

                    while (idx < fileList.length && foundFile == false) {
                        if (fileList[idx].equalsIgnoreCase(fileName)) {
                            foundFile = true;
                            fileName = fileList[idx];
                        } else {
                            idx++;
                        }
                    }
                }

        //  Append the file name
                pathStr.append(fileName);
            }

      //  Set the new path string
            mappedPath = pathStr.toString();
        }

    //  Return the mapped path string, if successful.
        return mappedPath;
    }

    /**
     * Open a file
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param params	File open parameters
     * @return NetworkFile
     * @exception IOException
     */
    public NetworkFile openFile(SrvSession sess, TreeConnection tree, FileOpenParams params)
            throws java.io.IOException {

    //  Create a Java network file
        DeviceContext ctx = tree.getContext();
        String fname = FileName.buildPath(ctx.getDeviceName(), params.getPath(), null, java.io.File.separatorChar);
        File file = new File(fname);
        if (file.exists() == false) {

      //  Try and map the file name string to a local path
            String mappedPath = mapPath(ctx.getDeviceName(), params.getPath());
            if (mappedPath == null) {
                throw new java.io.FileNotFoundException(fname);
            }

      //  Create the file object for the mapped file and check if the file exists
            file = new File(mappedPath);
            if (file.exists() == false) {
                throw new FileNotFoundException(fname);
            }
        }

    //	Check if the file is read-only and write access has been requested
        if (file.canWrite() == false && (params.isReadWriteAccess() || params.isWriteOnlyAccess())) {
            throw new AccessDeniedException("File " + fname + " is read-only");
        }

    //	Create the network file object for the opened file/folder
        NetworkFile netFile = new NIOJavaNetworkFile(file, params.getPath());

        if (params.isReadOnlyAccess()) {
            netFile.setGrantedAccess(NetworkFile.READONLY);
        } else {
            netFile.setGrantedAccess(NetworkFile.READWRITE);
        }

        netFile.setFullName(params.getPath());

    //  Check if the file is actually a directory
        if (file.isDirectory() || file.list() != null) {
            netFile.setAttributes(FileAttribute.Directory);
        }

    //  Return the network file
        return netFile;
    }

    /**
     * Read a block of data from a file
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param file	Network file
     * @param buf	Buffer to return data to
     * @param bufPos Starting position in the return buffer
     * @param siz	Maximum size of data to return
     * @param filePos	File offset to read data
     * @return Number of bytes read
     * @exception IOException
     */
    public int readFile(SrvSession sess, TreeConnection tree, NetworkFile file, byte[] buf, int bufPos, int siz, long filePos)
            throws java.io.IOException {

	  //	Check if the file is a directory
        if (file.isDirectory()) {
            throw new AccessDeniedException();
        }

    //  Read the file
        int rdlen = file.readFile(buf, siz, bufPos, filePos);

    //  If we have reached end of file return a zero length read
        if (rdlen == -1) {
            rdlen = 0;
        }

    //  Return the actual read length
        return rdlen;
    }

    /**
     * Rename a file
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param oldName	Existing file name
     * @param newName	New file name
     * @exception IOException
     */
    public void renameFile(SrvSession sess, TreeConnection tree, String oldName, String newName)
            throws java.io.IOException {

    //  Get the full path for the existing file and the new file name
        DeviceContext ctx = tree.getContext();
        String oldPath = FileName.buildPath(ctx.getDeviceName(), oldName, null, java.io.File.separatorChar);
        String newPath = FileName.buildPath(ctx.getDeviceName(), newName, null, java.io.File.separatorChar);

		//	Check if the current file/directory exists
        if (fileExists(sess, tree, oldName) == FileStatus.NotExist) {
            throw new FileNotFoundException("Rename file, does not exist " + oldName);
        }

		//	Check if the new file/directory exists
        if (fileExists(sess, tree, newName) != FileStatus.NotExist) {
            throw new FileExistsException("Rename file, path exists " + newName);
        }

    //  Rename the file
        File oldFile = new File(oldPath);
        File newFile = new File(newPath);

        if (oldFile.renameTo(newFile) == false) {
            throw new IOException("Rename " + oldPath + " to " + newPath + " failed");
        }
    }

    /**
     * Seek to the specified point within a file
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param file	Network file
     * @param pos	New file position
     * @param typ	Seek type
     * @return New file position
     * @exception IOException
     */
    public long seekFile(SrvSession sess, TreeConnection tree, NetworkFile file, long pos, int typ)
            throws java.io.IOException {

    //  Check that the network file is our type
        return file.seekFile(pos, typ);
    }

    /**
     * Set file information
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param name	File name
     * @param info	File information to be set
     * @exception IOException
     */
    public void setFileInformation(SrvSession sess, TreeConnection tree, String name, FileInfo info)
            throws java.io.IOException {

    //	Check if the modify date/time should be updated
        if (info.hasSetFlag(FileInfo.SetModifyDate)) {

      //	Build the path to the file
            DeviceContext ctx = tree.getContext();
            String fname = FileName.buildPath(ctx.getDeviceName(), name, null, java.io.File.separatorChar);

	    //	Update the file/folder modify date/time
            File file = new File(fname);
            file.setLastModified(info.getModifyDateTime());
        }
    }

    /**
     * Start a file search
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param searchPath	Search path, may include wildcards
     * @param attrib Search attributes
     * @return SearchContext
     * @exception FileNotFoundException
     */
    public SearchContext startSearch(SrvSession sess, TreeConnection tree, String searchPath, int attrib)
            throws java.io.FileNotFoundException {

    //  Create a context for the new search
        JavaFileSearchContext srch = new JavaFileSearchContext();

    //  Create the full search path string
        String path = FileName.buildPath(tree.getContext().getDeviceName(), null, searchPath, java.io.File.separatorChar);

        try {

			//	Map the path, this may require changing the case on some or all path components
            path = mapPath(path);

			//	DEBUG
            if (Debug.EnableInfo && sess != null && sess.hasDebug(SMBSrvSession.DBG_SEARCH)) {
                sess.debugPrintln("  Start search path=" + path);
            }

	    //  Initialize the search
            srch.initSearch(path, attrib);
            return srch;
        } catch (PathNotFoundException ex) {
            throw new FileNotFoundException();
        }
    }

    /**
     * Truncate a file to the specified size
     *
     * @param sess	Server session
     * @param tree Tree connection
     * @param file Network file details
     * @param siz New file length
     * @exception java.io.IOException The exception description.
     */
    public void truncateFile(SrvSession sess, TreeConnection tree, NetworkFile file, long siz)
            throws IOException {

	  //	Truncate or extend the file
        file.truncateFile(siz);
        file.flushFile();
    }

    /**
     * Write a block of data to a file
     *
     * @param sess	Session details
     * @param tree	Tree connection
     * @param file	Network file
     * @param buf	Data to be written
     * @param bufoff Offset of data within the buffer
     * @param siz	Number of bytes to be written
     * @param fileoff Offset within the file to start writing the data
     */
    public int writeFile(SrvSession sess, TreeConnection tree, NetworkFile file, byte[] buf, int bufoff, int siz, long fileoff)
            throws java.io.IOException {

    //	Check if the file is a directory
        if (file.isDirectory()) {
            throw new AccessDeniedException();
        }

		//	Write the data to the file
        file.writeFile(buf, siz, bufoff, fileoff);

    //  Return the actual write length
        return siz;
    }

    /**
     * Parse and validate the parameter string and create a device context for
     * this share
     *
     * @param shareName String
     * @param args ConfigElement
     * @return DeviceContext
     * @exception DeviceContextException
     */
    public DeviceContext createContext(String shareName, ConfigElement args)
            throws DeviceContextException {

  	//	Get the device name argument
        ConfigElement path = args.getChild("LocalPath");
        DiskDeviceContext ctx = null;

        if (path != null) {

  		//	Validate the path and convert to an absolute path
            File rootDir = new File(path.getValue());

  		//	Create a device context using the absolute path
            ctx = new DiskDeviceContext(rootDir.getAbsolutePath());

			//	Set filesystem flags
            ctx.setFilesystemAttributes(FileSystem.CasePreservedNames + FileSystem.UnicodeOnDisk);

			//	If the path is not valid then set the filesystem as unavailable
            if (rootDir.exists() == false || rootDir.isDirectory() == false || rootDir.list() == null) {

  		  //	Mark the filesystem as unavailable
                ctx.setAvailable(false);
            }

  		//	Return the context
            return ctx;
        }

  	//	Required parameters not specified
        throw new DeviceContextException("LocalPath parameter not specified");
    }

    /**
     * Connection opened to this disk device
     *
     * @param sess	Server session
     * @param tree Tree connection
     */
    public void treeOpened(SrvSession sess, TreeConnection tree) {
    }

    /**
     * Connection closed to this device
     *
     * @param sess	Server session
     * @param tree Tree connection
     */
    public void treeClosed(SrvSession sess, TreeConnection tree) {
    }

    /**
     * Return the global file creation date/time
     *
     * @return long
     */
    public final static long getGlobalCreateDateTime() {
        return _globalCreateDate;
    }

    /**
     * Return the lock manager implementation associated with this virtual
     * filesystem
     *
     * @param sess SrvSession
     * @param tree TreeConnection
     * @return LockManager
     */
    public LockManager getLockManager(SrvSession sess, TreeConnection tree) {
        return _lockManager;
    }
}
