package Jet.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import junit.framework.TestCase;

/**
 * A TestCase of cdb.Cdb and cdb.CdbBuilder
 * 
 * @author Akira ODA
 */
public class CdbTest extends TestCase {
    /** filename for test database */
    private static final String CDB_FILENAME = "test.cdb";

    @Override
    public void setUp() throws Exception {
        File file = new File(CDB_FILENAME);
        if (file.exists()) {
            System.gc();
            file.delete();
        }
    }

    @Override
    public void tearDown() throws Exception {
        File file = new File(CDB_FILENAME);
        if (file.exists()) {
            System.gc();
            file.deleteOnExit();
        }
    }

    /**
     * Tests cdb creation and lookup.
     */
    public void testBuildCdb() throws Exception {
        final int n = 1000;
        CdbBuilder builder = new CdbBuilder(CDB_FILENAME, CDB_FILENAME + ".tmp");
        Random r = new Random();
        List<byte[]> keys = new ArrayList<byte[]>(n);
        List<byte[]> values = new ArrayList<byte[]>(n);

        for (int i = 0; i < n; i++) {
            byte[] key = randData(r);
            byte[] value = randData(r);

            if (!checkDuplicate(key, keys)) {
                continue;
            }

            builder.add(key, value);
            keys.add(key);
            values.add(value);
        }
        builder.finish();

        // check phase
        Cdb cdb = new Cdb(CDB_FILENAME);
        for (int i = 0; i < keys.size(); i++) {
            byte[] key = keys.get(i);
            byte[] value = cdb.find(key, 0, key.length);

            assertTrue(Arrays.equals(values.get(i), value));
        }
        cdb.close();
    }

    /**
     * Make a random ascii string.
     */
    private byte[] randData(Random r) {
        int len = r.nextInt(1000) + 1;
        byte[] chars = new byte[len];
        for (int i = 0; i < len; i++) {
            chars[i] = (byte) (r.nextInt(127 - 32) + 32);
        }

        return chars;
    }

    /**
     * Checks duplicate key
     */
    private boolean checkDuplicate(byte[] key, List<byte[]> keys) {
        for (byte[] k : keys) {
            if (Arrays.equals(key, k)) {
                return false;
            }
        }

        return true;
    }
}
