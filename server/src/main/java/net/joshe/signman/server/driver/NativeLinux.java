package net.joshe.signman.server.driver;

// These native functions and constants are declared in a Java file because
// the Kotlin Gradle plugin doesn't output JNI headers for native functions.

class NativeLinux {
    static final byte PIN_STATE_INACTIVE = 0;
    static final byte PIN_STATE_ACTIVE = 1;
    static final byte PIN_STATE_MASK = 1;
    static final byte PIN_DIR_IN = 0;
    static final byte PIN_DIR_OUT = 1 << 1;
    @SuppressWarnings("unused")
    static final byte PIN_DIR_MASK = 1 << 1;
    static final byte PIN_ACTIVE_HIGH = 0;
    static final byte PIN_ACTIVE_LOW = 1 << 2;
    @SuppressWarnings("unused")
    static final byte PIN_ACTIVE_MASK = 1 << 2;

    static native long gpio_open(String device, String consumer, int[] lines, byte[] config);
    static native byte[] gpio_get(long chip, int[] lines);
    static native void gpio_set(long chip, int[] lines, byte[] values);
    static native void gpio_close(long chip);

    static final int SPI_CFG_MODE_MASK = 3;
    static final int SPI_CFG_LSB_FIRST = 1 << 2;
    static final int SPI_CFG_HALF_DUPLEX = 1 << 3;

    static native int spi_open(String device, int hz, int mode);
    static native byte[] spi_io(int fd, byte[] output, int inputLen, boolean split);
    static native void spi_close(int fd);
}
