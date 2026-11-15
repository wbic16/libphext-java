package io.phext.libphext;

/**
 * The Java form of libphext-rs {@code PhextParseError}: thrown by
 * {@link Coordinate#tryParse(String)} when an address is not of the form
 * LB.SF.SR/CN.VM.BK/CH.SN.SC with parseable components.
 */
public class PhextParseException extends RuntimeException {
    public PhextParseException() {
        super("Phext addresses are of the form LB.SF.SR/CN.VM.BK/CH.SN.SC");
    }
}
