#Requirements
- Since original assignment requirements for the filesystem are minimal (create, write, read, remove files),
I thought of a typical filesystem. It should be able to create, read, search, list files and folders. Also
if some files are removed, there are gaps, which could be removed by defragmentation. Filesystem should provide
methods to check available space and remove everything (format).
- Filesystem operate files, so files should have some set of attributes to work with. I thought of a filename and
file content.
- Filesystem should be thread safe because multiple clients may use it.
- If client using filesystem is down, filesystem should not disappear. All contents must be restored when the client is
up again.

#Implementation
- How to work with file: after some googling, I came up to the conclusion that the best way to work with files is 
through MappedByteBuffer
which represents the whole file as a byte array.
- How to instantiate filesystem: because filesystem should be only one, singleton pattern seemed to me like a 
perfect fit. But many developers consider implementation of this pattern through static getter and private constructor
as a bad practice. So I decided not to hurt anyone's feelings and used Spring dependency injection (bean has a 
single instance by default).
- Bytebuffers offset, length, size are represented by int.class. So my initial decision to use long values cancelled.
This limitation can be overcome, if array of MappedByteBuffer is used, but I needed more time to implement this.
- How to make filesystem thread safe: first I identified the invariants that constrain the state variables. They are 
fileSystemTree map, currentPosition int. To not block the whole instance of the file system for read, write and remove 
actions, I decided to use ReentrantReadWriteLock.

#Further possible improvements
- support folders
- support long position and size instead of int
- add possibility for a user to specify location of a file for the filesystem
