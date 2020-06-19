package home.work.system;

/**
 * Wrapper class for the filename and bytes of content
 *
 */
public class File {
    private String name;
    private int nameLength;
    private byte[] content;
    private int contentLength;

    /**
     * Creates a new File instance. If the content array is empty, byte array for "-1" is used.
     *
     */
    public File(String filename, byte[] content) {
        this.name = filename;
        this.nameLength = filename.getBytes().length;
        if (content.length == 0) {
            this.content = new byte[] {(byte)(-1 >>> 24), (byte)(-1 >>> 16), (byte)(-1 >>> 8), (byte)-1}; //-1
        } else {
            this.content = content;
        }
        this.contentLength = this.content.length;
    }

    public byte[] getContent() {
        return content;
    }

    public int getContentLength() {
        return contentLength;
    }

    public int getNameLength() {
        return nameLength;
    }

    public String getName() {
        return name;
    }

    /**
     * Calculates total length in bytes required to write this file.
     * Total length consists of {@link #nameLength}, {@link #contentLength},
     * 1 byte needed for isRemoved flag, 4 bytes for the size of the filename,
     * and 4 bytes for the size of the content
     *
     * @return  calculated total space needed to write a file
     *
     */
    public int getTotalLength() {
        return nameLength + contentLength + 2 * 4 + 1;
    }
}
