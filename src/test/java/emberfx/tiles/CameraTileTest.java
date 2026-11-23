package emberfx.tiles;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CameraTileTest {
    private static final byte SOI_1 = (byte) 0xFF, SOI_2 = (byte) 0xD8, EOI_2 = (byte) 0xD9;

    @Test
    void framesAreSplitOnJpegStartAndEndMarkers() throws Exception {
        byte[] stream = {
            '-', '-', 'b', 'o', 'u', 'n', 'd', '\r', '\n', // multipart header noise before the first frame
            SOI_1, SOI_2, 1, 2, SOI_1, EOI_2,              // frame 1
            'x', 'y',                                      // noise between frames
            SOI_1, SOI_2, 3, SOI_1, EOI_2,                 // frame 2
            SOI_1, SOI_2, 4};                              // cut off mid-frame
        ByteArrayInputStream in = new ByteArrayInputStream(stream);

        assertArrayEquals(new byte[] {SOI_1, SOI_2, 1, 2, SOI_1, EOI_2}, CameraTile.nextJpeg(in));
        assertArrayEquals(new byte[] {SOI_1, SOI_2, 3, SOI_1, EOI_2}, CameraTile.nextJpeg(in));
        assertNull(CameraTile.nextJpeg(in), "an unfinished frame at the end of the stream is dropped");
    }
}
