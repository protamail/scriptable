package com.bkmks;

/**
 * Can be thrown by Soy compiler task methods
 */
public class CompileTaskException extends Exception
{
    public String sourcePath;
    public int lineNumber, colNumber;

    public CompileTaskException(String detail, String sourcePath, int lineNumber, int colNumber) {
        super(detail);
        this.sourcePath = sourcePath;
        this.lineNumber = lineNumber;
        this.colNumber = colNumber;
    }
}

