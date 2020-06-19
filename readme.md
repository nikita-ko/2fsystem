2fsystem
----------------------------------------

This is a simple filesystem based on a single file.

[FileSystem](FileSystem.java) is used to perform all the operations, which include:
* Create file
* Read file
* Remove file
* Defragment filesystem
* Format filesystem
* List filenames of all existing files
* Check if the file exists in the system
* Get available space

[FileSystemDriver](FileSystemDriver.java) is a proxy to access FileSystem. 
All clients are supposed to use FileSystemDriver instance to use FileSystem.
Thread safety of FileSystem allows multiple instances of FileSystemDriver operate 
at the same time. Examples of its usage can be found in [FileSystemDriverTest](FileSystemDriverTest.java)

[File](File.java) is a wrapped of String filename and byte[] content which represent a file. 

## Assumptions and limitations

* Filesystem is flat, meaning there are only files, not folders. 
Hence, files with the same name are not allowed, but can be overwritten.
* Max allowed file size is limited by Integer.MAX_VALUE (about 2GB). 
This is because internal implementation of FileSystem uses int.
* If the app was using FileSystem stopped, FileSystem can be restored 
from the file "fileSystem" at the next start.
* To ensure there is only one instance of FileSystem Spring dependency injection is used